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

package org.opendc.simulator.network.components.networks

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.Serializable
import org.opendc.common.units.Unit.Companion.averageOfUnitOrNull
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal abstract class NetworkImpl<Self : Network<Self>> : Network<Self> {
    abstract override val sendNodesById: Map<NodeId, SenderNode<*>>

    abstract override val nodesById: Map<NodeId, Node<*>>

    override val flowsById: MutableMap<FlowId, INetFlow> = mutableMapOf()

    override operator fun get(nId: NodeId): Node<*>? = this.nodesById[nId]

    context(NetSimScope)
    override suspend fun startFlow(f: INetFlow) {
        val senderN = sendNodesById[f.senderId]!!
        f.senderNode = senderN

        routPolicy.onFlowStart(f)

        assert(f.destId in nodesById)
        senderN.startFlow(f)
        flowsById += f.id to f
    }

    context(NetSimScope)
    override suspend fun stopFlow(f: INetFlow) {
        ctx[RoutPolicy]?.onFlowStop(f)

        sendNodesById[f.senderId]!!.stopFlow(f)
        flowsById -= f.id
    }

    context(NetSimScope)
    override suspend fun fmtNodes(mode: NetSimStabilityMode): String =
        barrier.whileStable {
            "\n" +
                """
                | === NETWORK INFO ===
                | nodes: ${this.nodesById.size - 1}
                | switches: ${getNodesById<Switch>().size}
                | global switches: ${getNodesById<Switch>().values.count { it.global }}
                | hosts: ${getNodesById<Terminal>().size}
                """.trimIndent()
        }

    context(NetSimScope)
    override suspend fun fmtFlows(
        mode: NetSimStabilityMode,
        ls: Boolean,
    ): String =
        barrier.whileStable(mode) {
            val snap = net.snapshot()
            val f = flowsById.values

            buildString {
                if (ls) {
                    appendLine("==== Flows ====")
                    appendLine(
                        " | " +
                            "id".padEnd(10) +
                            "senderIp".padEnd(20) +
                            "destIp".padEnd(20) +
                            "demand".padEnd(20) +
                            "throughput".padEnd(20),
                    )
                    flowsById.values.forEach { flow ->
                        appendLine(
                            " | " +
                                flow.id.toString().padEnd(10) +
                                flow.senderId.toIp().toString().padEnd(20) +
                                flow.destId.toIp().toString().padEnd(20) +
                                flow.demand.fmtValue("%.3f").padEnd(20) +
                                flow.throughput.fmtValue("%.3f").padEnd(20),
                        )
                    }
                }
                appendLine("==== Overview ====")
                appendLine(
                    " | " +
                        "avg-demand".padEnd(15) +
                        "avg-tput".padEnd(15) +
                        "max-demand".padEnd(15) +
                        "max-tput".padEnd(15) +
                        "min-demand".padEnd(15) +
                        "min-tput".padEnd(15) +
                        "avg-tput [%]".padEnd(15) +
                        "min-tput [%]".padEnd(15) +
                        "tot-demand".padEnd(15) +
                        "tot-tput".padEnd(15) +
                        "tot-tput [%]".padEnd(15),
                )
                appendLine(
                    " | " +
                        f.averageOfUnitOrNull { it.demand }?.fmtValue("%.3f")?.padEnd(15) +
                        f.averageOfUnitOrNull { it.throughput }?.fmtValue("%.3f")?.padEnd(15) +
                        f.maxOfOrNull { it.demand }?.fmtValue("%.3f")?.padEnd(15) +
                        f.maxOfOrNull { it.throughput }?.fmtValue("%.3f")?.padEnd(15) +
                        f.minOfOrNull { it.demand }?.fmtValue("%.3f")?.padEnd(15) +
                        f.minOfOrNull { it.throughput }?.fmtValue("%.3f")?.padEnd(15) +
                        snap.avrgTputPerc?.fmtValue("%.3f")?.padEnd(15) +
                        snap.worstTputPerc?.fmtValue("%.3f")?.padEnd(15) +
                        f.sumOfUnit { it.demand }.fmtValue("%.3f").padEnd(15) +
                        f.sumOfUnit { it.throughput }.fmtValue("%.3f").padEnd(15) +
                        snap.totTputPerc?.fmtValue("%.3f")?.padEnd(15),
                )
            }
        }

    companion object {
        internal inline fun <reified T : Node<*>> NetworkImpl<*>.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { it.id }
        }

        /**
         * [NodeId] reserved for inet representation (for inter-datacenter communication).
         *
         * Corresponds to ip address 255.255.255.255.
         */
        val INTERNET_ID: NodeId = NodeId(UInt.MAX_VALUE)
    }
}
