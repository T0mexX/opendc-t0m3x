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

package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.internalstructs.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.node.internalstructs.routtbl.RoutTbl2
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
@Polymorphic
internal sealed class RoutPolicy : AbstractCoroutineContextElement(Key) {
    /**
     * Determines which core switches flows coming from [Internet] will be routed to.
     */
    abstract val internetRoutPolicy: RoutPolicy

    context(NetSimScope)
    open suspend fun checkRequirements() { /* NoOps */ }

    /**
     * Selects the ports to forward [nodeFEntry]'s [NetFlow] to,
     * updating the entry related fields.
     */
    context(NetSimScope, Node<*>)
    abstract suspend fun selectPorts(nodeFEntry: NodeFlowEntry)

    /**
     * Invoked after network construction.
     */
    context(NetSimScope)
    internal open suspend fun setUp() { /* NoOps */ }

    /**
     * It performs necessary actions so that the flow is correctly routed through the network.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStart(f: INetFlow) { /* NoOps */ }

    /**
     * It performs necessary actions so that all resources associated with the flow routing are freed.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStop(f: INetFlow) { /* NoOps */ }

    /**
     * It performs the necessary updates due to node addition.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onNodeAdded(n: Node<*>) { /* NoOps */ }

    /**
     * It performs necessary actions so that flows that where
     * routed through the removed node are rerouted correctly.
     */
    context(NetSimScope)
    internal open suspend fun onNodeRemoved(n: Node<*>) { /* NoOps */ }

    companion object Key : CoroutineContext.Key<RoutPolicy> {
        /**
         * Filters `this` collection of [RoutingTable.PossiblePath], keeping only those that are minimal.
         */
        @JvmStatic
        protected fun Collection<RoutTbl2.RoutTblPath>.onlyMinimal(): Collection<RoutTbl2.RoutTblPath> {
            val min: Int = this.minOfOrNull { it.distance } ?: 0
            return this.filter { it.distance == min }
        }
    }
}
