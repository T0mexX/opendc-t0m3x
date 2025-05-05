package org.opendc.simulator.network.components.networks

import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.components.specs.DragonFlySpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * @see DragonFlySpecs for network parameters.
 *
 * source: https://dl.acm.org/doi/abs/10.1145/1394608.1382129
 */
internal class DragonFly private constructor(
    val specs: DragonFlySpecs,
    groups: List<Group>,
    override val inet: Internet,
): NetworkImpl() {
    override val _nodesById: MutableMap<NodeId, Node<*>> = buildMap {
        putAll(groups.flatMap { it.hosts }.associateBy { it.id })
        putAll(groups.flatMap { it.switches }.associateBy { it.id })
        put(inet.id, inet)
    }.toMutableMap()

    override val _sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    override val _nodeLs: MutableList<Node<*>> =
        _nodesById.values.toMutableList()

    override fun toSpecs(): Specs<Network> = specs

    companion object {
        /**
         * Suspending constructor.
         * @param specs The specs of the dragonfly topology to build.
         */
        context(NetSimScope)
        suspend operator fun invoke(
            specs: DragonFlySpecs,
        ): DragonFly {
            val int = Internet()

            // Build groups and their internal connections.
            val groups = 0.rangeUntil(specs.g).map { Group(specs, int) }

            fun Switch.isConnectedTo(other: Switch): Boolean =
                this.ports.any { it.txLink?.receiverPort?.owner == other }

            fun Switch.expectedNConnections(): Int =
                if (this is GlobalSwitch) (specs.a - 1) + specs.h + specs.p + 1
                else (specs.a - 1) + specs.h + specs.p

            fun Switch.nConnections(): Int = this.ports.count { it.txLink != null }

            fun Switch.nMissingConnections(): Int = expectedNConnections() - nConnections()

            fun <T> List<T>.getModuloIdx(idx: Int): T = this[idx % this.size]

            fun Group.nConnectionsWith(other: Group): Int =
                switches.sumOf { thisSw ->
                    other.switches.count { otherSw ->
                        otherSw.isConnectedTo(thisSw)
                    }
                }

            // Establish intergroup connections.
            val nConns = 0.until(specs.a).associateWith { 0 }.toMutableMap()
            var toGDeltaI = 1
            do {
                val fromSwI = nConns.entries.find { it.value < specs.h }?.key ?: break
                nConns.compute(fromSwI) { _, v -> v!! + 1 }

                val toSwI = nConns.entries.find { it.value < specs.h }!!.key
                nConns.compute(toSwI) { _, v -> v!! + 1 }

                groups.forEachIndexed { gIdx, g ->
                    val targetG = groups.getModuloIdx(gIdx + toGDeltaI)
                    val toSw = targetG.switches.getModuloIdx(toSwI)
                    assert(targetG !== g)
                    g.switches[fromSwI].connectTo(toSw)
                }

                toGDeltaI = (toGDeltaI + 1) % specs.g
                if (toGDeltaI == 0) {
                    toGDeltaI = 1
                }
            } while (true)

            // Assert all switches have exactly the number of expected connections.
            assert(groups.all { it.switches.all { sw -> sw.nMissingConnections() == 0 } })

            // Assert "each pair of groups connected by at least (ah+1)/g channels", see source.
            assert(
                groups.all { g1 ->
                    groups.filter { it !== g1 }
                        .all { g2 ->
                            g1.nConnectionsWith(g2) >= (specs.a * specs.h + 1) / specs.g
                        }
                }
            )

            return DragonFly(
                specs = specs,
                groups = groups,
                inet = int,
            ).also {
                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Register the network in the simulation scope.
                this@NetSimScope.registerNetwork(it)
            }
        }
    }

    /**
     * A group consists of a routers connected via an intra-group interconnection
     * network formed from local channels. Each group has [DragonFlySpecs.a] * [DragonFlySpecs.p]
     * connections to terminals and [DragonFlySpecs.a] * [DragonFlySpecs.h]
     * connections to global channels.
     */
    private class Group private constructor(
        val hosts: List<HostNode>,
        val switches: List<Switch>,
        ) {

        companion object {
            /**
             * Suspending constructor.
             */
            context(NetSimScope)
            suspend operator fun invoke(specs: DragonFlySpecs, int: Internet): Group {
                // Remaining global switches to add to group.
                var glSwNum = specs.globalSwitchesPerGroup

                // Build switches in the group.
                val switches = 0.rangeUntil(specs.a).map {
                    if (--glSwNum >= 0) specs.switchSpecs.toCoreSwitchSpecs().buildAsCore(int)
                    else specs.switchSpecs.build()
                }

                // Establish connections between switches of the same group (pairwise connections).
                switches.forEachIndexed { idx, sw1 ->
                    switches.drop(idx + 1).forEach { sw2 ->
                        sw1.connectTo(sw2)
                    }
                }

                // Establish connection between each switch and terminals (hosts).
                val hosts = buildList {
                    switches.forEach { sw ->
                        0.rangeUntil(specs.p).map {
                            specs.hostSpecs.build()
                        }.forEach { h ->
                            h.connectTo(sw)
                            add(h)
                        }
                    }
                }

                return Group(hosts, switches)
            }
        }
    }
}
