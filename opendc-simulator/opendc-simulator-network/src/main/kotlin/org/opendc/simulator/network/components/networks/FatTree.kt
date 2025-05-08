package org.opendc.simulator.network.components.networks

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.components.specs.FatTreeSpecs
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable
import kotlin.math.pow

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class FatTree private constructor(
    private val specs: FatTreeSpecs,
    nodesById: Map<NodeId, Node<*>>,
    override val inet: Internet,
): NetworkImpl() {
    override val _nodesById: MutableMap<NodeId, Node<*>> =
        nodesById.toMutableMap()
    override val _nodeLs: MutableList<Node<*>> = ArrayList(_nodesById.values)

    override val _sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    override fun toSpecs(): Specs<FatTree> = specs

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String = barrier.whileStable(mode) {
        """
            === Network (FatTree) ===
            | k: ${specs.k}
            | levels: 3
            | nodes: ${nodeLs.size - 1}
            | switches: ${getNodesById<Switch>().size}
            | global switches: ${getNodesById<GlobalSwitch>().size}
            | hosts: ${getNodesById<HostNode>().size}
        """.trimIndent()
    }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            specs: FatTreeSpecs,
        ): FatTree {
            val inet = Internet()

            /**
             * Parameter that determines the topology which is defined as
             * equal to the minimum number of ports of all switches rounded down to even number.
             * Ideally, all switches should have the same number of ports.
             * This value has to be even and larger than 2.
             */
            val k: Int = listOf(specs.crSwSpecs, specs.aggrSwSpecs, specs.accessSwSpecs).minOf { it.nPorts() } / 2 * 2
            require(k % 2 == 0 && k > 2) { "Fat tree can only be built with even-port-number (>2) switches" }

            this@NetSimScope.log.info("building fat-tree with k=$k")
            val pods = buildList { repeat(k) { add(getPod(specs.aggrSwSpecs, specs.accessSwSpecs, specs.hostSpecs)) } }

            val coreSwitchesChunked =
                buildList {
                    repeat(k * k / 4) {
                        add(
                            specs.crSwSpecs.toCoreSwitchSpecs().buildAsCore(inet)
                        )
                    }
                }.chunked(k / 2)

            pods.forEach { pod ->
                pod.aggrSwitches.forEachIndexed { switchIdx, switch ->
                    coreSwitchesChunked[switchIdx].forEach { it.connectTo(switch) }
                }
            }

            val coreSwitches = coreSwitchesChunked.flatten()
            val aggregationSwitches = pods.flatMap { it.aggrSwitches }
            val torSwitches = pods.flatMap { it.torSwitches }
            val leafs = pods.flatMap { it.hostNodes }

            val nodesById =
                buildMap {
                    putAll((leafs + torSwitches + aggregationSwitches + coreSwitches).associateBy { it.id })
                    check(inet.id !in this) {
                        "unable to create network: one node has id ${NetworkImpl.INTERNET_ID}, " +
                            "which is reserved for inet abstraction"
                    }
                    put(inet.id, inet)
                }.toMutableMap()

            return FatTree(
                specs = specs,
                nodesById = nodesById,
                inet = inet,
            ).also {
                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Setup global fairness policy if needed.
                this@NetSimScope.config.fairPolicy.setUp()

                // Register the network in the simulation scope.
                this@NetSimScope.registerNetwork(it)
            }
        }

        context(NetSimScope)
        private suspend fun getPod(
            aggrSpecs: SwitchSpecs,
            torSpecs: SwitchSpecs,
            hostNodeSpecs: HostNodeSpecs,
        ): FatTreePod {
            val k: Int = listOf(aggrSpecs,torSpecs).minOf { it.nPorts() }

            val hostNodes =
                buildList {
                    repeat((k / 2).toDouble().pow(2.0).toInt()) { add(hostNodeSpecs.build()) }
                }

            val torSwitches =
                buildList {
                    repeat(k / 2) { add(torSpecs.build()) }
                }

            hostNodes.forEachIndexed { index, server ->
                server.connectTo(torSwitches[index / (k / 2)])
            }

            val aggrSwitches =
                torSwitches
                    .map { _ ->
                        val newSwitch = aggrSpecs.build()
                        torSwitches.forEach { newSwitch.connectTo(it) }
                        newSwitch
                    }.toList()

            return FatTreePod(hostNodes = hostNodes, aggrSwitches = aggrSwitches, torSwitches = torSwitches)
        }
    }

    /**
     * TODO
     */
    private class FatTreePod(
        val hostNodes: List<HostNode>,
        val torSwitches: List<Switch>,
        val aggrSwitches: List<Switch>,
    )
}
