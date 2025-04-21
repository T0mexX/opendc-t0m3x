package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable
import javax.naming.OperationNotSupportedException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
@Serializable(with = NonSerializable::class)
@Polymorphic
internal sealed class RoutPolicy: AbstractCoroutineContextElement(Key) {

    /**
     * Selects the ports to forward [nodeFlowEntry]'s [NetFlow] to,
     * updating the entry related fields.
     */
    context(NetSimScope, Node<*>)
    abstract suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry)

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
    internal open suspend fun onFlowStarted(f: NetFlow) { /* NoOps */ }

    /**
     * It performs necessary actions so that all resources associated with the flow routing are freed.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStopped(f: NetFlow) { /* NoOps */ }

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
        protected fun Collection<RoutingTable.PossiblePath>.onlyMinimal(): Collection<RoutingTable.PossiblePath> {
            val min: Int = this.minOfOrNull { it.numOfHops } ?: 0
            return this.filter { it.numOfHops == min }
        }
    }
}
