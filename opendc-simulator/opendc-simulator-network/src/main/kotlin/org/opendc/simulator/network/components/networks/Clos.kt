package org.opendc.simulator.network.components.networks

import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.components.specs.ClosSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode

/**
 * Network of [ClosSpecs.n] fully connected layers. Last layer is of [HostNode]s.
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
        """
            === Network (Clos) ===
            | n: ${specs.n}
            | nodesPerLayer: ${layers.map { it.size }}
            | nodes: ${nodeLs.size - 1}
            | switches: ${getNodesById<Switch>().size}
            | global switches: ${getNodesById<GlobalSwitch>().size}
            | hosts: ${getNodesById<HostNode>().size}
        """.trimIndent()

    companion object {
        /**
         * Suspending constructor.
         */
        context(NetSimScope) suspend operator fun invoke(specs: ClosSpecs): Clos {
            val inet = Internet()
            val layers = buildList {
                repeat(specs.n + 1) { add(mutableListOf<Node<*>>()) }
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
                repeat(specs.nodesPerLayer[layerIdx]!!) {
                    layer.add(
                        when (layerIdx) {
                            // Higher layer of global switches.
                            0 -> GlobalSwitch(portSpeed = speed, nPorts = nBelow + 1)

                            // Lower layer of hosts.
                            specs.n -> HostNode(portSpeed = speed, nPorts = nAbove)

                            // All layers in the middle of switches.
                            else -> Switch(portSpeed = speed, nPorts = nAbove + nBelow)
                        }
                    )
                }
            }

            // Connect layers.
            layers.forEachIndexed { layerIdx, layer ->
                layer.forEach { n ->
                    // Connect the uppermost layer to the internet.
                    if (layerIdx == 0) {
                        n.connectTo(inet)
                    }

                    // Connect to the layer below.
                    layers.getOrNull(layerIdx + 1)?.forEach { nBelow ->
                        n.connectTo(nBelow)
                    }
                }
            }

            return Clos(
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
    }
}
