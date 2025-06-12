package org.opendc.simulator.network.components.networks

import kotlinx.serialization.Serializable
import org.opendc.common.units.Unit.Companion.averageOfUnitOrNull
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.specs.Specs
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

    override val evntFlow = EvntFlow<Network>()

    override operator fun get(nId: NodeId): Node<*>? = this.nodesById[nId]

    context(NetSimScope)
    override suspend fun startFlow(f: INetFlow) {
        val senderN = sendNodesById[f.senderId]!!
        f.senderNode = senderN

        routPolicy.onFlowStart(f)

        assert(f.destId in _nodesById)
        senderN.startFlow(f)
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
                | nodes: ${this.nodesById.size - 1}
                | switches: ${getNodesById<GlobalSwitch>().size}
                | global switches: ${getNodesById<GlobalSwitch>().size}
                | hosts: ${getNodesById<HostNode>().size}
                """.trimIndent()
        }

    override fun toSpecs(): Specs<Network> = specs

    context(NetSimScope)
    override suspend fun fmtFlows(mode: NetSimStabilityMode, ls: Boolean): String =
        barrier.whileStable(mode) {
            val snap = net.snapshot()
            val f =_flows.values

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
                        "tot-tput [%]".padEnd(15)
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
                        snap.totTputPerc?.fmtValue("%.3f")?.padEnd(15)
                )
            }
        }

    companion object {
        internal inline fun <reified T : Node<*>> NetworkImpl.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { it.id }
        }

        /**
         * [NodeId] reserved for inet representation (for inter-datacenter communication).
         *
         * Corresponds to ip address 255.255.255.255.
         */
        val INTERNET_ID: NodeId = NodeId(UInt.MAX_VALUE.toLong())
    }
}
