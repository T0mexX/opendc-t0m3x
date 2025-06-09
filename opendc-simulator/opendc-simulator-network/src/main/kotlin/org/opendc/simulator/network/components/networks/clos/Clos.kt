package org.opendc.simulator.network.components.networks.clos

import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.withProgressBar
import kotlin.system.measureTimeMillis

/**
 * Network of [ClosSpecs.N_] fully connected layers. Last layer is of [HostNode]s.
 */
internal class Clos(
    val specs: ClosSpecs,
    private val layers: List<List<Node<*>>>,
    override val inet: Internet,
): NetworkImpl() {
    override val _nodesById: MutableMap<NodeId, Node<*>> =
        layers.flatten().associateBy { it.id }.toMutableMap().also {
            it[inet.id] = inet
        }

    override val _sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    override val _nodeLs: MutableList<Node<*>> =
        _nodesById.values.toMutableList()

    override fun toSpecs(): Specs<Network> = specs

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String =
        super.fmt(mode) +
        """
            ${'\u200B'}
             | n (layers): ${specs.n}
             | nodes per layer: ${specs.nodesPerLayer}
        """.trimIndent()

    companion object {
        /**
         * Suspending constructor.
         */
        context(NetSimScope) suspend operator fun invoke(
            specs: ClosSpecs
        ): Clos = withProgressBar<Clos>(task = "Building Clos Network...", max = specs.E_.toLong() + specs.V_) pb@ {
            // TODO: change impl.
            // Only same size layers supported now.
            require(specs.nodesPerLayer.values.toList().dropLast(1).all { it == specs.nodesPerLayer.values.first() })

            val updtOnConnect = this@NetSimScope.devConfig.netConfig.closConfig.updtRoutingOnEachConnect
            val inet = Internet()
            val layers = buildList {
                specs.nodesPerLayer.values.forEach {
                    add(ArrayList<Node<*>>(it))
                }
            }

            val hostSubnets = (0..<specs.nodesPerLayer.values.first()).map {
                // The subnet includes a switch of layer n-1 and all the hosts connected to it.
                addrMngr.getNewSubNet(nIps = specs.k / 2 + 1)
            }

            // Build layers.
            layers.forEachIndexed { layerIdx, layer ->
                // Number of nodes in the higher layer if any.
                val nAbove = specs.nodesPerLayer[layerIdx - 1] ?: 0

                // Number of nodes in the lower layer if any.
                val nBelow = specs.nodesPerLayer[layerIdx + 1] ?: 0

                // Port speed of the nodes in the layer.
                val speed = specs.portSpeedPerLayer[layerIdx]!!

                // Build layer.
                (0..<specs.nodesPerLayer[layerIdx]!!).forEach { idx ->
                    layer.add(
                        when (layerIdx) {
                            // Higher layer of global switches.
                            0 -> GlobalSwitch(portSpeed = speed, nPorts = nBelow + 1)

                            // Lower layer of hosts.
                            specs.n -> HostNode(portSpeed = speed, nPorts = 1, subnet = hostSubnets[idx / (specs.k / 2)])

                            // Layer above hosts.
                            specs.n - 1 -> Switch(portSpeed = speed, nPorts = nAbove + nBelow, subnet = hostSubnets[idx])

                            // All layers in the middle of switches.
                            else -> Switch(portSpeed = speed, nPorts = nAbove + nBelow)
                        }
                    )
                    this@pb.step()
                }
            }

            // Connect layers.
            layers.dropLast(2).forEachIndexed { lIdx, layer ->
                layer.forEachIndexed { nIdx, n ->
                    // Connect the uppermost layer to the internet.
                    if (lIdx == 0) {
                        n.msgSyncConnect(inet, updtRoutTbl = updtOnConnect)
                    }

                    // The layer below.
                    val nextL = layers[lIdx + 1]
                    // Downward network radix.
                    val kDwn = specs.k / 2

                    // Connect each node `n` to `k/2` nodes of the layer below.
                    (0..<kDwn).forEach { deltaIdx ->
                        // The node in the layer below to connect to.
                        val nTarget = nextL.getModuloIdx(nIdx + deltaIdx)
                        n.msgSyncConnect(nTarget, updtRoutTbl = updtOnConnect)
                        this@pb.step()
                    }
                }

                if (updtOnConnect.not()) {
                    layer.first().msgAsyncShareRoutVect()
                    layers[lIdx + 1].first().msgAsyncShareRoutVect()
                    barrier.awaitStability()
                }

                println("layer $lIdx completed")
            }

            val aboveHostLayer = layers[layers.size - 2]
            val hostLayer = layers.last()
            hostLayer.forEachIndexed { hIdx, h ->
                val s = aboveHostLayer[hIdx / (specs.k / 2)]
                h.msgSyncConnect(s, updtRoutTbl = updtOnConnect)
                this@pb.step()
            }

            hostLayer.first().msgAsyncShareRoutVect()

            inet.msgAsyncShareRoutVect()
            barrier.awaitStability()

            // Assert built topology corresponds to specs.
            assert(layers.sumOf { it.size } == specs.V_)
            assert(layers.dropLast(1).sumOf { it.size } == specs.R_)
            assert(layers.last().size == specs.N_)
            assert(this@pb.current == specs.E_.toLong() + specs.V_)
            assert(layers.first().all { it.links.count { it != null } == specs.k / 2 + 1})
            assert(layers.dropLast(1).drop(1).flatten().all { it.links.count { it != null } == specs.k })
            assert(layers.last().all { it.links.count { it != null } == 1 })

            Clos(
                specs = specs,
                layers = layers,
                inet = inet,
            ).also {
                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Register the network in the simulation scope.
                this@NetSimScope.registerNetwork(it)
            }
        }

        private fun <T> List<T>.getModuloIdx(idx: Int): T = this[idx % this.size]
    }
}
