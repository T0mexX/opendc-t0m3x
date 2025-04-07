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
import org.opendc.simulator.network.utils.NonSerializable

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal interface Network : WithSpecs<Network> {
    val sendNodesById: Map<NodeId, SenderNode>

    val nodesById: Map<NodeId, Node>

    val flowsById: Map<FlowId, INetFlow>

    val internet: Internet

    operator fun get(nId: NodeId): Node?

    context(NetSimScope)
    suspend fun startFlow(f: INetFlow)

    context(NetSimScope)
    suspend fun stopFlow(f: INetFlow)

    context(NetSimScope)
    suspend fun fmtNodes(mode: NetSimStabilityMode = config.stabilityMode): String

    context(NetSimScope)
    suspend fun fmtFlows(mode: NetSimStabilityMode = config.stabilityMode): String

    companion object {
        internal inline fun <reified T : Node> Network.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { it.id }
        }
    }
}
