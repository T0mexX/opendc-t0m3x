package org.opendc.simulator.network.components.networks

import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
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
        context(NetSimScope) suspend operator fun invoke(specs: ClosSpecs): Clos {
            // Progress bar used while building network.
            val pb = ProgressBarBuilder()
                // Each step is building a node or adding a link.
                .setInitialMax(specs.E_.toLong() + specs.V_)
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Building DragonFly Network...")
                .build()

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
                    pb.step()
                }
            }

            // Connect layers.
            layers.forEachIndexed { layerIdx, layer ->
                layer.forEach { n ->
                    // Connect the uppermost layer to the internet.
                    if (layerIdx == 0) {
                        n.msgSyncConnect(inet)
                    }

                    // Connect to the layer below.
                    layers.getOrNull(layerIdx + 1)?.forEach { nBelow ->
                        n.msgSyncConnect(nBelow)
                        pb.step()
                    }
                }
            }

            // Assert built topology corresponds to specs.
            assert(layers.sumOf { it.size } == specs.V_)
            assert(layers.dropLast(1).sumOf { it.size } == specs.R_)
            assert(layers.last().size == specs.N_)
            assert(pb.current == specs.E_.toLong() + specs.V_)

            return Clos(
                specs = specs,
                layers = layers,
                inet = inet,
            ).also {
                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Setup global fairness policy if needed.
                this@NetSimScope.config.fairPolicy.setUp()

                // Register the network in the simulation scope.
                this@NetSimScope.registerNetwork(it)

                pb.close()
            }
        }
    }
}
