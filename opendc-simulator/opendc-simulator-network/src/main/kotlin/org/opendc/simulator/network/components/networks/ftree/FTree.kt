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

package org.opendc.simulator.network.components.networks.ftree

import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.node.switchh.SwitchSpecs
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.components.node.terminal.TerminalSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.common.withProgressBarSus
import kotlin.math.pow

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class FTree private constructor(
    override val specs: FatTreeSpecs,
    nodesById: Map<NodeId, Node<*>>,
    override val inet: Internet,
    val pods: List<FTreePod>,
) : NetworkImpl<FTree>() {
    override val nodesById: MutableMap<NodeId, Node<*>> =
        nodesById.toMutableMap()

    override val sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String =
        barrier.whileStable(mode) {
            super.fmt(mode) +
                """
                ${'\u200B'}
                 | k (pods): ${specs.k}
                """.trimIndent()
        }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(specs: FatTreeSpecs): FTree =
            withProgressBarSus(task = "Building FatTree Network...", max = specs.E_.toLong() + specs.V_) pb@{
                val inet = Internet()

                /**
                 * Parameter that determines the topology which is defined as
                 * equal to the minimum number of ports of all switches rounded down to even number.
                 * Ideally, all switches should have the same number of ports.
                 * This value has to be even and larger than 2.
                 */
                /**
                 * Parameter that determines the topology which is defined as
                 * equal to the minimum number of ports of all switches rounded down to even number.
                 * Ideally, all switches should have the same number of ports.
                 * This value has to be even and larger than 2.
                 */
                val k: Int = listOf(specs.crSwSpecs, specs.aggrSwSpecs, specs.accessSwSpecs).minOf { it.nPorts() } / 2 * 2
                require(k % 2 == 0 && k > 2) { "Fat tree can only be built with even-port-number (>2) switches" }

                // The `k` pods.
                val pods =
                    buildList {
                        repeat(k) {
                            add(
                                getPod(
                                    specs.aggrSwSpecs,
                                    specs.accessSwSpecs,
                                    specs.hostSpecs,
                                ),
                            )
                        }
                    }

                val coreSwitchesChunked =
                    buildList {
                        repeat(k * k / 4) {
                            add(
                                specs.crSwSpecs.buildAsGlobal(inet = inet, updtRoutTbl = false),
                            )
                        }
                    }.chunked(k / 2)
                this@pb.stepBy(k.toLong() * k / 4)

                pods.forEach { pod ->
                    pod.aggrSwitches.forEachIndexed { switchIdx, switch ->
                        coreSwitchesChunked[switchIdx].forEach { it.msgSyncConnect(switch, updtRoutTbl = false) }
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
                assert(nodesById.values.filterIsInstance<Terminal>().size == specs.N_)
                assert(this@pb.current == specs.E_.toLong() + specs.V_)

                inet.msgAsyncShareRoutVect()
                barrier.awaitStability()

                FTree(
                    specs = specs,
                    nodesById = nodesById,
                    inet = inet,
                    pods = pods,
                ).also {
                    // Setup global routing policy if needed.
                    this@NetSimScope.config.routPolicy.setUp()

                    // Register the network in the simulation rootScope.
                    this@NetSimScope.registerNetwork(it)
                }
            }

        context(NetSimScope, ProgressBar)
        private suspend fun getPod(
            aggrSpecs: SwitchSpecs,
            torSpecs: SwitchSpecs,
            hostNodeSpecs: TerminalSpecs,
        ): FTreePod {
            val k: Int = listOf(aggrSpecs, torSpecs).minOf { it.nPorts() }
            val nodesPerPod: Int = (k.toDouble().pow(2) / 4 + k).toInt()

            val subnet =
                // Create a new subnet in the global env which contains at least `nodesPerPod` ips.
                if (devConfig.netConfig.subnetOpt) {
                    addrMngr.getNewSubNet(nIps = nodesPerPod)

                    // Else use "0.0.0.0/0" as a subnet (equivalent to no subnet)
                } else {
                    addrMngr.globalPrefix
                }

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
                server.msgSyncConnect(torSwitches[index / (k / 2)], updtRoutTbl = false)
            }
            this@ProgressBar.stepBy(hostNodes.size.toLong())

            val aggrSwitches =
                torSwitches
                    .map { _ ->
                        val newSwitch = aggrSpecs.build(subnet = subnet)
                        torSwitches.forEach { newSwitch.msgSyncConnect(it, updtRoutTbl = false) }
                        this@ProgressBar.stepBy(torSwitches.size.toLong() + 1)
                        newSwitch
                    }.toList()

            hostNodes.first().msgAsyncShareRoutVect()
            barrier.awaitStability()

            return FTreePod(hosts = hostNodes, aggrSwitches = aggrSwitches, torSwitches = torSwitches)
        }
    }

    /**
     * TODO
     */
    data class FTreePod(
        val hosts: List<Terminal>,
        val torSwitches: List<Switch>,
        val aggrSwitches: List<Switch>,
    )
}
