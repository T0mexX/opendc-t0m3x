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
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.internalstructs.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.node.internalstructs.routtbl.RoutTblImpl
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
     * Invoked after network construction.
     */
    context(NetSimScope)
    internal open suspend fun setUp() { /* NoOps */ }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // On Network Changes
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * It performs necessary actions so that the flow is correctly routed through the network.
     *
     * This method needs to be invoked by the [TODO] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStart(f: INetFlow) { /* NoOps */ }

    /**
     * It performs necessary actions so that all resources associated with the flow routing are freed.
     *
     * This method needs to be invoked by the [TODO] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStop(f: INetFlow) { /* NoOps */ }

    /**
     * It performs the necessary updates due to node addition.
     *
     * This method needs to be invoked by the [TODO] main job.
     */
    context(NetSimScope)
    internal open suspend fun onNodeAdded(n: Node<*>) { /* NoOps */ }

    /**
     * It performs necessary actions so that flows that where
     * routed through the removed node are rerouted correctly.
     */
    context(NetSimScope)
    internal open suspend fun onNodeRemoved(n: Node<*>) { /* NoOps */ }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // On Node Changes
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Called when a new flow is first received at this node.
     *
     * This hook can be used to initialize routing state.
     * In case of static routing, this function should be the only one determining the routing of flows.
     * ([onNodeFUpdt] & [onNodeTxAttempt] should not be overridden).
     *
     * @param nodeFEntry The metadata associated with the newly received flow.
     * @return `true` if the tentative tput should be sent to set again on the links, `false` otherwise.
     */
    context(NetSimScope, Node<*>)
    abstract suspend fun onNodeNewFReceived(nodeFEntry: NodeFlowEntry): Boolean

    /**
     * Called whenever a node updates the amount of data received for a flow.
     *
     * This is typically used to immediately recalculate and adjust tx attempts
     * on outgoing links in response to a flow rx update.
     *
     * The default implementation does not change routing,
     * only changes the tx rates on the links based on the changed rx.
     */
    context(NetSimScope, Node<*>)
    internal open suspend fun onNodeFUpdt(
        updt: Node.RxUpdt,
        entry: NodeFlowEntry,
    ): Boolean = true

    /**
     * Called before a node attempts to update transmission rates to its links.
     *
     * This hook allows recomputing or adjusting transmission decisions after all
     * flow rx changes have been registered.
     * It is preferred over [onNodeFUpdt] when routing or transmission computations
     * are more efficient when done in batch after all rx updates.
     */
    context(NetSimScope, Node<*>)
    internal open suspend fun onNodeTxAttempt() { /* NoOps */ }

    companion object Key : CoroutineContext.Key<RoutPolicy> {
        /**
         * TODO: change
         * Filters `this` collection of [RoutingTable.PossiblePath], keeping only those that are minimal.
         */
        @JvmStatic
        protected fun Collection<RoutTblImpl.RoutTblPath>.onlyMinimal(): Collection<RoutTblImpl.RoutTblPath> {
            val min: Int = this.minOfOrNull { it.distance } ?: 0
            return this.filter { it.distance == min }
        }
    }
}
