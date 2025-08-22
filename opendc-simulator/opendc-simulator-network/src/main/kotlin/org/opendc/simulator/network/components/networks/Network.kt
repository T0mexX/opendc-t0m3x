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
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal interface Network<Self : Network<Self>> {
    /**
     * TODO
     */
    val sendNodesById: Map<NodeId, SenderNode<*>>

    /**
     * TODO
     */
    val nodesById: Map<NodeId, Node<*>>

    /**
     * TODO
     */
    val flowsById: Map<FlowId, INetFlow>

    /**
     * TODO
     */
    val inet: Internet

    /**
     * TODO
     */
    val specs: NetSpecs<Self>

    operator fun get(nId: NodeId): Node<*>?
    operator fun get(ip: IPv4Address): Node<*>?
    operator fun get(fId: FlowId): INetFlow?
    operator fun contains(nId: NodeId): Boolean
    operator fun contains(ip: IPv4Address): Boolean
    operator fun contains(fId: FlowId): Boolean

    context(NetSimScope)
    suspend fun startFlow(f: INetFlow)

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun stopFlow(f: INetFlow)

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun fmtNodes(mode: NetSimStabilityMode = config.stabilityMode): String

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun fmtFlows(
        mode: NetSimStabilityMode = config.stabilityMode,
        ls: Boolean = true,
    ): String

    context(NetSimScope)
    suspend fun fmt(mode: NetSimStabilityMode = config.stabilityMode): String =
        barrier.whileStable(mode) {
            """
            | === Network (${this::class.simpleName}) ===
            | V (nodes/vertices)    : ${specs.V_}
            | N (hosts)             : ${specs.N_}
            | R (switches/routers)  : ${specs.R_}
            | E (links/edges)       : ${specs.E_}
            | global switches       : ${nodesById.values.count { it is Switch && it.global }}
            """.trimIndent()
        }

    companion object {
        /**
         * TODO
         */
        internal inline fun <reified T> Network<*>.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { (it as Node<*>).id }
        }
    }
//
//
//    interface FlowStarted: Evnt<FlowStarted, Network>, Invalidatable {
//        val f: NetFlow
//
//        companion object : FWId<FlowStarted>
//    }
//
//    interface FlowStopped: Evnt<FlowStopped, Network>, Invalidatable {
//        val f: NetFlow
//
//        companion object : FWId<FlowStopped>
//    }
//
//    interface NodeAdded: Evnt<NodeAdded, Network>, Invalidatable {
//        val node: Node<*>
//
//        companion object : FWId<NodeAdded>
//    }
//
//    interface NodeRemoved: Evnt<NodeRemoved, Network>, Invalidatable {
//        val node: Node<*>
//
//        companion object : FWId<NodeRemoved>
//    }
}
