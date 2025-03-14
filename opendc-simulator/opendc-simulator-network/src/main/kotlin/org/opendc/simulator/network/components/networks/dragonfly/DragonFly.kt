/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.components.networks.dragonfly

import me.tongfei.progressbar.ProgressBar
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.withProgressBar

/**
 * @see DFSpecs for network parameters.
 *
 * source: https://dl.acm.org/doi/abs/10.1145/1394608.1382129
 */
internal class DragonFly private constructor(
    override val specs: DFSpecs,
    val groups: List<DFGroup>,
    override val inet: Internet,
) : NetworkImpl<DragonFly>() {
    override val nodesById: MutableMap<NodeId, Node<*>> =
        buildMap {
            putAll(groups.flatMap { it.hosts }.associateBy { it.id })
            putAll(groups.flatMap { it.switches }.associateBy { it.id })
            put(inet.id, inet)
        }.toMutableMap()

    override val sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String =
        barrier.whileStable(mode) {
            super.fmt(mode) +
                """
                ${'\u200B'}
                 | g (groups): ${specs.g}
                 | a (routers per group): ${specs.a}
                 | p (terminal per router): ${specs.p}
                 | h (inter-group edges per router): ${specs.h}
                """.trimIndent()
        }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Suspending Constructor
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        /**
         * Suspending constructor.
         * @param specs The specs of the dragonfly topology to build.
         */
        context(NetSimScope)
        suspend operator fun invoke(specs: DFSpecs): DragonFly =
            withProgressBar(task = "Building DragonFly...", max = specs.E_.toLong() + specs.V_) pb@{
                val inet = Internet()

                // Build groups and their internal connections.
                val groups = 0.rangeUntil(specs.g).map { DFGroup(specs, inet) }

                fun Switch.isConnectedTo(other: Switch): Boolean = this.links.any { it?.receiverN === other }

                fun Switch.expectedNConnections(): Int =
                    if (this.global) {
                        (specs.a - 1) + specs.h + specs.p + 1
                    } else {
                        (specs.a - 1) + specs.h + specs.p
                    }

                fun Switch.nConnections(): Int = this.links.count { it != null }

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

                    val toSwI =
                        nConns.entries.find {
                            it.value < specs.h &&
                                // "From" and "to" indices to be different to avoid circular connection
                                // (E_.g., group1 to group 3, group 3 to group 1)
                                it.key != fromSwI &&
                                // Connection between these 2 switch indexes at group distance `toGDeltaI` has to be missing.
                                groups[0].switches[fromSwI].isConnectedTo(groups[toGDeltaI].switches[it.key]).not()
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
                    this@pb.stepBy(groups.size.toLong())

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

                // Assert "each pair of groups connected by at least (ah+1)/g channels".
                assert(
                    groups.all { g1 ->
                        groups.filter { it !== g1 }
                            .all { g2 ->
                                assert(g1.nConnectionsWith(g2) >= (specs.a * specs.h + 1) / specs.g) {
                                    "${g1.nConnectionsWith(g2)}, ${(specs.a * specs.h + 1) / specs.g}"
                                }
                                g1.nConnectionsWith(g2) >= (specs.a * specs.h + 1) / specs.g
                            }
                    },
                )

                // Assert the number of vertices (nodes) in the network is the one derived from the specs.
                assert(
                    specs.V_ == groups.sumOf { it.switches.size + it.hosts.size },
                ) { "${specs.V_} ${groups.sumOf { it.switches.size + it.hosts.size }}" }

                // Assert progress bar consistency.
                assert(this@pb.current == specs.E_.toLong() + specs.V_) { "${this@pb.current} ${specs.E_.toLong() + specs.V_}" }

                DragonFly(
                    specs = specs,
                    groups = groups,
                    inet = inet,
                ).also {
                    // Setup global routing policy if needed.
                    this@NetSimScope.config.routPolicy.setUp()

                    // Register the network in the simulation rootScope.
                    this@NetSimScope.registerNetwork(it)
                }
            }
    }

    /**
     * A group consists of a routers connected via an intra-group interconnection
     * network formed from local channels. Each group has [DFSpecs.a] * [DFSpecs.p]
     * connections to terminals and [DFSpecs.a] * [DFSpecs.h]
     * connections to global channels.
     */
    internal class DFGroup private constructor(
        val hosts: List<Terminal>,
        val switches: List<Switch>,
    ) {
        companion object {
            /**
             * Suspending constructor.
             */
            context(NetSimScope, ProgressBar)
            suspend operator fun invoke(
                specs: DFSpecs,
                inet: Internet,
            ): DFGroup {
                // Remaining global switches to add to group.
                var glSwNum = specs.globalSwitchesPerGroup

                val subnet =
                    // Create a new subnet in the global rootScope that can contain all the nodes in the group.
                    if (devConfig.netConfig.subnetOpt) {
                        addrMngr.getNewSubNet(nIps = specs.a * specs.p + specs.a)

                        // Else use "0.0.0.0/0" as a subnet (equivalent to no subnet)
                    } else {
                        addrMngr.globalPrefix
                    }

                // Build switches in the group.
                val switches =
                    0.rangeUntil(specs.a).map {
                        if (--glSwNum >= 0) {
                            specs.switchSpecs.buildAsGlobal(inet = inet, updtRoutTbl = false, subnet = subnet)
                        } else {
                            specs.switchSpecs.build(subnet = subnet, inet = inet)
                        }
                    }
                this@ProgressBar.stepBy(switches.size.toLong())

                // Establish connections between switches of the same group (pairwise connections).
                switches.forEachIndexed { idx, sw1 ->
                    switches.drop(idx + 1).forEach { sw2 ->
                        sw1.msgSyncConnect(sw2, updtRoutTbl = false)
                    }
                    this@ProgressBar.stepBy(switches.size.toLong() - idx - 1)
                }

                // Establish connection between each switch and terminals (hosts).
                val hosts =
                    buildList {
                        switches.forEach { sw ->
                            0.rangeUntil(specs.p).map {
                                this@ProgressBar.step()
                                specs.hostSpecs.build(subnet = subnet)
                            }.forEach { h ->
                                h.msgSyncConnect(sw, updtRoutTbl = false)
                                add(h)
                                this@ProgressBar.step()
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
