package org.opendc.simulator.network.components.networks.dragonfly

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
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

/**
 * @see DragonFlySpecs for network parameters.
 *
 * source: https://dl.acm.org/doi/abs/10.1145/1394608.1382129
 */
internal class DragonFly private constructor(
    val specs: DragonFlySpecs,
    val groups: List<DFGroup>,
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

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String = barrier.whileStable(mode) {
        super.fmt(mode) +
            """
                ${'\u200B'}
                 | g (groups): ${specs.g}
                 | a (routers per group): ${specs.a}
                 | p (terminal per router): ${specs.p}
                 | h (inter-group edges per router): ${specs.h}
            """.trimIndent()
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Suspending Constructor
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        /**
         * Suspending constructor.
         * @param specs The specs of the dragonfly topology to build.
         */
        context(NetSimScope)
        suspend operator fun invoke(
            specs: DragonFlySpecs,
        ): DragonFly {
            // Progress bar used while building network.
            val pb = ProgressBarBuilder()
                // Each step is building a node or adding a link.
                .setInitialMax(specs.E_.toLong() + specs.V_)
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Building DragonFly Network...")
                .build()

            val inet = Internet()

            // Build groups and their internal connections.
            val groups = 0.rangeUntil(specs.g).map { DFGroup(specs, inet, pb) }

            fun Switch.isConnectedTo(other: Switch): Boolean =
                this.ports.any { it.txLink?.receiverPort?.owner == other }

            fun Switch.expectedNConnections(): Int =
                if (this is GlobalSwitch) (specs.a - 1) + specs.h + specs.p + 1
                else (specs.a - 1) + specs.h + specs.p

            fun Switch.nConnections(): Int = this.ports.count { it.txLink != null }

            fun Switch.nMissingConnections(): Int = expectedNConnections() - nConnections()

            fun <T> List<T>.getModuloIdx(idx: Int): T = this[idx % this.size]

            fun DFGroup.nConnectionsWith(other: DFGroup): Int =
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

                val toSwI = nConns.entries.find {
                    it.value < specs.h
                        // "From" and "to" indices to be different to avoid circular connection
                        // (E_.g., group1 to group 3, group 3 to group 1)
                        && it.key != fromSwI
                        // Connection between these 2 switch indexes at group distance `toGDeltaI` has to be missing.
                        && groups[0].switches[fromSwI].isConnectedTo(groups[toGDeltaI].switches[it.key]).not()
                }!!.key
                nConns.compute(toSwI) { _, v -> v!! + 1 }

                groups.forEachIndexed { gIdx, g ->
                    val targetG = groups.getModuloIdx(gIdx + toGDeltaI)
                    val fromSw = g.switches[fromSwI]
                    val toSw = targetG.switches[toSwI]
                    assert(targetG !== g)
                    assert(fromSw.isConnectedTo(toSw).not())
                    g.switches[fromSwI].msgSyncConnect(toSw, updtRoutTbl = false)
                }

                // Update progress bar.
                pb.stepBy(groups.size.toLong())

                toGDeltaI = (toGDeltaI + 1) % specs.g
                if (toGDeltaI == 0) {
                    toGDeltaI = 1
                }
            } while (true)

            // Trigger routing info propagation for intergroup links.
            groups.first().switches.first().msgAsyncShareRoutVect()
            barrier.awaitStability()

            // Assert all switches have exactly the number of expected connections.
            assert(groups.all { it.switches.all { sw -> sw.nMissingConnections() == 0 } })

            // Assert "each pair of groups connected by at least (ah+1)/g channels", see source.
            assert(
                groups.all { g1 ->
                    groups.filter { it !== g1 }
                        .all { g2 ->
                            assert(g1.nConnectionsWith(g2) >= (specs.a * specs.h + 1) / specs.g) {
                                "${g1.nConnectionsWith(g2)}, ${(specs.a * specs.h + 1) / specs.g}"
                            }
                            g1.nConnectionsWith(g2) >= (specs.a * specs.h + 1) / specs.g
                        }
                }
            )

            // Assert the number of vertices (nodes) in the network is the one derived from the specs.
            assert(specs.V_ == groups.sumOf { it.switches.size + it.hosts.size }) {"${specs.V_} ${groups.sumOf { it.switches.size + it.hosts.size }}"}

            // Assert progress bar consistency.
            assert(pb.current == specs.E_.toLong() + specs.V_) {"${pb.current} ${specs.E_.toLong() + specs.V_}"}

            return DragonFly(
                specs = specs,
                groups = groups,
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

    /**
     * A group consists of a routers connected via an intra-group interconnection
     * network formed from local channels. Each group has [DragonFlySpecs.a] * [DragonFlySpecs.p]
     * connections to terminals and [DragonFlySpecs.a] * [DragonFlySpecs.h]
     * connections to global channels.
     */
    internal class DFGroup private constructor(
        val hosts: List<HostNode>,
        val switches: List<Switch>,
        ) {

        companion object {
            /**
             * Suspending constructor.
             */
            context(NetSimScope)
            suspend operator fun invoke(specs: DragonFlySpecs, inet: Internet, pb: ProgressBar): DFGroup {
                // Remaining global switches to add to group.
                var glSwNum = specs.globalSwitchesPerGroup

                // Build switches in the group.
                val switches = 0.rangeUntil(specs.a).map {
                    if (--glSwNum >= 0) specs.switchSpecs.toGlobalSwitchSpecs().buildAsCore(inet, updtRoutTbl = false)
                    else specs.switchSpecs.build()
                }
                pb.stepBy(switches.size.toLong())

                // Establish connections between switches of the same group (pairwise connections).
                switches.forEachIndexed { idx, sw1 ->
                    switches.drop(idx + 1).forEach { sw2 ->
                        sw1.msgSyncConnect(sw2, updtRoutTbl = false)
                    }
                    pb.stepBy(switches.size.toLong() - idx - 1)
                }

                // Establish connection between each switch and terminals (hosts).
                val hosts = buildList {
                    switches.forEach { sw ->
                        0.rangeUntil(specs.p).map {
                            pb.step()
                            specs.hostSpecs.build()
                        }.forEach { h ->
                            h.msgSyncConnect(sw, updtRoutTbl = false)
                            add(h)
                            pb.step()
                        }
                    }
                }

                // Trigger routing table info propagation in this group.
                switches.first().msgAsyncShareRoutVect()
                barrier.awaitStability()

                return DFGroup(hosts, switches)
            }
        }
    }
}
