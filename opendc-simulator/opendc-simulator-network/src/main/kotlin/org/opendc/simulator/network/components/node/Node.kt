package org.opendc.simulator.network.components.node

import kotlinx.coroutines.Job
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.specs.WithSpecs
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.notifiable.publics.AnsweredNotification
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification


/**
 * Interface representing a node in a [Network2].
 */
internal interface Node : WithSpecs<Node>, IInvalidatable, Notifiable<Node>, Launchable {
    /**
     * ID of the node. Uniquely identifies the node in the [Network22].
     */
    val id: NodeId

    /**
     * Port speed in Kbps full duplex.
     */
    val portSpeed: DataRate

    /**
     * Number of ports of ***this*** [Node].
     */
    val nPorts: Int

    val ports: List<Port>

    val job: Job?

    /**
     * Policy that determines to which [Port]s the flowsById are forwarded to.
     */
    var routingPolicy: RoutingPolicy

    /**
     * Policy that determines how the flowsById data are handled in case of maximum bw reached.
     */
    var fairnessPolicy: FairnessPolicy

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routingTable: RoutingTable

    val flowTable: FlowTable

    suspend fun connectTo(other: Node, linkBw: DataRate = this.portSpeed min other.portSpeed)

    suspend fun disconnectFrom(other: Node)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    interface RxUpdate: Notification<Node>, IFW<RxUpdate> {
        var netFlow: NetFlow
        var deltaRate: DataRate

        companion object : FWId<RxUpdate>
    }

    interface Connect: Notification<Node>, IFW<Connect> {
        var other: Node
        var linkBw: DataRate

        companion object : FWId<Connect>
    }

    interface Disconnect: Notification<Node>, IFW<Disconnect> {
        var other: Node
        var notifyOther: Boolean

        companion object : FWId<Disconnect>
    }

    interface ReapplyRouting: Notification<Node>, IFW<ReapplyRouting> {

        companion object : FWId<ReapplyRouting>
    }

    interface AcceptConnection: AnsweredNotification<Node, Port>, IFW<AcceptConnection> {
        var toBeAccepted: Port
        var linkBw: DataRate

        companion object : FWId<AcceptConnection>
    }




//    override suspend fun totIncomingDataRateOf(fId: FlowId): DataRate = flowHandler.outgoingFlows[fId]?.demand.ifNull0()
//
//    override fun totOutgoingDataRateOf(fId: FlowId): DataRate = flowHandler.outgoingFlows[fId]?.totRateOut.ifNull0()
//
//    override fun allTransitingFlowsIds(): Collection<FlowId> =
//        with(flowHandler) {
//            outgoingFlows.keys + consumingFlows.keys
//        }

//    /**
//     * @return formatted string representing node information. Preferably to be logged in a new line.
//     */
//    fun fmt(): String =
//        """
//        | Type = ${this::class.simpleName}
//        | numPorts = $nPorts
//        | portSpeed = $portSpeed
//        | numConnectedNodes = $numOfConnectedNodes
//        """.trimIndent()
}


