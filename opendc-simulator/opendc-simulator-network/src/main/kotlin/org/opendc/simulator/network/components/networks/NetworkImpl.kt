package org.opendc.simulator.network.components.networks

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.evntemitter.publics.EvntFlow

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal abstract class NetworkImpl : Network {

    override val sendNodesById: Map<NodeId, SenderNode<*>> get() = _sendNodesById
    protected abstract val _sendNodesById: MutableMap<NodeId, SenderNode<*>>

    override val nodesById: Map<NodeId, Node<*>> get() = _nodesById
    protected abstract val _nodesById: MutableMap<NodeId, Node<*>>

    override val flowsById: Map<FlowId, INetFlow> get() = _flows
    protected val _flows: MutableMap<FlowId, INetFlow> = mutableMapOf()

    override val nodeLs: List<Node<*>> get() = _nodeLs
    protected abstract val _nodeLs: MutableList<Node<*>>

    override val evntFlow = EvntFlow<Network>()

    override operator fun get(nId: NodeId): Node<*>? = this.nodesById[nId]

    context(NetSimScope)
    override suspend fun startFlow(f: INetFlow) {
        ctx[RoutPolicy]?.onFlowStart(f)

        sendNodesById[f.senderId]!!.startFlow(f)
        _flows += f.id to f
    }

    context(NetSimScope)
    override suspend fun stopFlow(f: INetFlow) {
        ctx[RoutPolicy]?.onFlowStop(f)

        sendNodesById[f.senderId]!!.stopFlow(f)
        _flows -= f.id
    }


    context(NetSimScope)
    override suspend fun fmtNodes(mode: NetSimStabilityMode): String =
        barrier.whileStable {
            "\n" +
                """
                | === NETWORK INFO ===
                | nodes: ${this.nodeLs.size - 1}
                | switches: ${getNodesById<GlobalSwitch>().size}
                | global switches: ${getNodesById<GlobalSwitch>().size}
                | hosts: ${getNodesById<HostNode>().size}
                """.trimIndent()
        }

    context(NetSimScope)
    override suspend fun fmtFlows(mode: NetSimStabilityMode): String =
        barrier.whileStable(mode) {
            buildString {
                appendLine("| ==== Flows ====")
                appendLine(
                    "| " +
                        "id".padEnd(5) +
                        "sender".padEnd(10) +
                        "dest".padEnd(10) +
                        "demand".padEnd(20) +
                        "throughput".padEnd(20),
                )
                flowsById.values.forEach { flow ->
                    appendLine(
                        "| " +
                            flow.id.toString().padEnd(5) +
                            flow.senderId.toString().padEnd(10) +
                            flow.destId.toString().padEnd(10) +
                            flow.demand.fmtValue("%.3f").padEnd(20) +
                            flow.throughput.fmtValue("%.3f").padEnd(20),
                    )
                }
            }
        }

    companion object {
        internal inline fun <reified T : Node<*>> NetworkImpl.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { it.id }
        }

        /**
         * [NodeId] reserved for inet representation (for inter-datacenter communication).
         */
        val INTERNET_ID: NodeId = NodeId(Long.MIN_VALUE)
    }
}
