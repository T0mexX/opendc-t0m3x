package org.opendc.simulator.network.components.networks.ftree

import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.withProgressBar
import kotlin.math.pow

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class FTree private constructor(
    val specs: FatTreeSpecs,
    nodesById: Map<NodeId, Node<*>>,
    override val inet: Internet,
    val pods: List<FTreePod>,
): NetworkImpl() {
    override val _nodesById: MutableMap<NodeId, Node<*>> =
        nodesById.toMutableMap()
    override val _nodeLs: MutableList<Node<*>> = ArrayList(_nodesById.values)

    override val _sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    override fun toSpecs(): Specs<FTree> = specs

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String = barrier.whileStable(mode) {
        super.fmt(mode) +
            """
                ${'\u200B'}
                 | k (pods): ${specs.k}
            """.trimIndent()
    }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            specs: FatTreeSpecs,
        ): FTree = withProgressBar(task = "Building FatTree Network...", max = specs.E_.toLong() + specs.V_) pb@ {
            val fTreeConfig = devConfig.netConfig.ftreeConfig

            val inet = Internet()

            // Determines if routing table should be updated progressively
            // while the network is being built or at the end.
            val updtRout = fTreeConfig.buildSteps == 1

            /**
             * Parameter that determines the topology which is defined as
             * equal to the minimum number of ports of all switches rounded down to even number.
             * Ideally, all switches should have the same number of ports.
             * This value has to be even and larger than 2.
             */
            val k: Int = listOf(specs.crSwSpecs, specs.aggrSwSpecs, specs.accessSwSpecs).minOf { it.nPorts() } / 2 * 2
            require(k % 2 == 0 && k > 2) { "Fat tree can only be built with even-port-number (>2) switches" }

            // The `k` pods.
            val pods = buildList { repeat(k) {
                add(getPod(
                    specs.aggrSwSpecs,
                    specs.accessSwSpecs,
                    specs.hostSpecs,
                    fTreeConfig
                ))
            } }

            val coreSwitchesChunked =
                buildList {
                    repeat(k * k / 4) {
                        add(
                            specs.crSwSpecs.toGlobalSwitchSpecs().buildAsCore(inet, updtRoutTbl = updtRout)
                        )
                    }
                }.chunked(k / 2)
            this@pb.stepBy(k.toLong() * k / 4)

            pods.forEach { pod ->
                pod.aggrSwitches.forEachIndexed { switchIdx, switch ->
                    coreSwitchesChunked[switchIdx].forEach { it.msgSyncConnect(switch, updtRoutTbl = updtRout) }
                    this@pb.stepBy(coreSwitchesChunked[switchIdx].size.toLong())
                }
            }

            val coreSwitches = coreSwitchesChunked.flatten()
            val aggregationSwitches = pods.flatMap { it.aggrSwitches }
            val torSwitches = pods.flatMap { it.torSwitches }
            val leafs = pods.flatMap { it.hosts }

            val nodesById =
                buildMap {
                    putAll((leafs + torSwitches + aggregationSwitches + coreSwitches).associateBy { it.id })
                    check(inet.id !in this) {
                        "unable to create network: one node has id $INTERNET_ID, " +
                            "which is reserved for inet abstraction"
                    }
                    put(inet.id, inet)
                }.toMutableMap()


            // Assert built topology corresponds to specs.
            assert(nodesById.size - 1 == specs.V_)
            assert(nodesById.values.filterIsInstance<Switch>().size == specs.R_)
            assert(nodesById.values.filterIsInstance<HostNode>().size == specs.N_)
            assert(this@pb.current == specs.E_.toLong() + specs.V_)

            if (fTreeConfig.buildSteps > 1) inet.msgAsyncShareRoutVect()

            FTree(
                specs = specs,
                nodesById = nodesById,
                inet = inet,
                pods = pods,
            ).also {
                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Setup global fairness policy if needed.
                this@NetSimScope.config.fairPolicy.setUp()

                // Register the network in the simulation scope.
                this@NetSimScope.registerNetwork(it)
            }
        }

        context(NetSimScope, ProgressBar)
        private suspend fun getPod(
            aggrSpecs: SwitchSpecs,
            torSpecs: SwitchSpecs,
            hostNodeSpecs: HostNodeSpecs,
            fTreeConfig: FTreeConfig,
        ): FTreePod {
            val updtRoutOnConnect = fTreeConfig.buildSteps == 1
            val updtRoutOnPodBuilt = fTreeConfig.buildSteps == 3

            val k: Int = listOf(aggrSpecs,torSpecs).minOf { it.nPorts() }
            val nodesPerPod: Int = (k.toDouble().pow(2) / 4 + k).toInt()


            val subnet =
                // Create a new subnet in the global scope which contains at least `nodesPerPod` ips.
                if (devConfig.netConfig.useSubnets) addrMngr.getNewSubNet(nIps = nodesPerPod)
                // Else use "0.0.0.0/0" as a subnet (equivalent to no subnet)
                else addrMngr.globalPrefix

            val hostNodes =
                buildList {
                    repeat((k / 2).toDouble().pow(2.0).toInt()) { add(hostNodeSpecs.build(subnet = subnet)) }
                }
            this@ProgressBar.stepBy(hostNodes.size.toLong())

            val torSwitches =
                buildList {
                    repeat(k / 2) { add(torSpecs.build(subnet = subnet)) }
                }
            this@ProgressBar.stepBy(torSwitches.size.toLong())

            hostNodes.forEachIndexed { index, server ->
                server.msgSyncConnect(torSwitches[index / (k / 2)], updtRoutTbl = updtRoutOnConnect)
            }
            this@ProgressBar.stepBy(hostNodes.size.toLong())

            val aggrSwitches =
                torSwitches
                    .map { _ ->
                        val newSwitch = aggrSpecs.build(subnet = subnet)
                        torSwitches.forEach { newSwitch.msgSyncConnect(it, updtRoutTbl = updtRoutOnConnect) }
                        this@ProgressBar.stepBy(torSwitches.size.toLong() + 1)
                        newSwitch
                    }.toList()

            if (updtRoutOnPodBuilt) {
                hostNodes.first().msgAsyncShareRoutVect()
                barrier.awaitStability()
            }

            return FTreePod(hosts = hostNodes, aggrSwitches = aggrSwitches, torSwitches = torSwitches)
        }
    }

    /**
     * TODO
     */
    internal class FTreePod(
        val hosts: List<HostNode>,
        val torSwitches: List<Switch>,
        val aggrSwitches: List<Switch>,
    )
}
