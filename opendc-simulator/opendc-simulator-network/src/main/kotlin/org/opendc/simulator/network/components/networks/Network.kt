package org.opendc.simulator.network.components.networks

import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.specs.WithSpecs
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.evntemitter.publics.Evnt
import org.opendc.simulator.network.utils.evntemitter.publics.EvntEmitter
import org.opendc.simulator.network.utils.evntemitter.publics.IEvntEmitter
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal interface Network : WithSpecs<Network>, IEvntEmitter<Network> {
    /**
     * TODO
     */
    val routPolicy: RoutPolicy

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
     * performant indexing of nodes
     */
    val nodeLs: List<Node<*>>

    /**
     * TODO
     */
    val flowsById: Map<FlowId, INetFlow>

    /**
     * TODO
     */
    val internet: Internet

    /**
     * TODO
     */
    operator fun get(nId: NodeId): Node<*>?

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun launchNodes() {
        nodesById.values.forEach { with (it) { netLaunch() } }
    }

    /**
     * TODO
     */
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
    suspend fun fmtFlows(mode: NetSimStabilityMode = config.stabilityMode): String

    companion object {
        /**
         * TODO
         */
        internal inline fun <reified T> Network.getNodesById(): Map<NodeId, T> {
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
