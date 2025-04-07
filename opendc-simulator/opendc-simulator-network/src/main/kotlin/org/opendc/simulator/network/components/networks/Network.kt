package org.opendc.simulator.network.components.networks

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.specs.WithSpecs
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.NonSerializable

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal interface Network : WithSpecs<Network> {
    /**
     * TODO
     */
    val sendNodesById: Map<NodeId, SenderNode>

    /**
     * TODO
     */
    val nodesById: Map<NodeId, Node>

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
    operator fun get(nId: NodeId): Node?

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
        internal inline fun <reified T : Node> Network.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { it.id }
        }
    }
}
