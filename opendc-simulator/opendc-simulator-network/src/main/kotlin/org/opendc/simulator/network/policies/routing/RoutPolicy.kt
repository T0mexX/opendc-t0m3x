package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.internalstructs.RoutTbl2
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.NetFlow
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
internal sealed class RoutPolicy: AbstractCoroutineContextElement(Key) {
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
