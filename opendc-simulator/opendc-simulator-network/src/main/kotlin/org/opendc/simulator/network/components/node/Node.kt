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
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification


/**
 * Interface representing a node in a [Network2].
 */
internal interface Node : WithSpecs<Node>, Invalidatable, Notifiable<Node>, Launchable {
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

    val job: Job

    /**
     * Policy that determines to which [Port]s the flows are forwarded to.
     */
    var portSelectionPolicy: RoutingPolicy

    /**
     * Policy that determines how the flows data are handled in case of maximum bw reached.
     */
    var fairnessPolicy: FairnessPolicy

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routingTable: RoutingTable

    val flowTable: FlowTable

    suspend fun connectTo(other: Node, linkBw: DataRate = this.portSpeed min other.portSpeed)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    interface RxUpdate: Notification<Node>, IFW<RxUpdate> {
        var netFlow: NetFlow
        var deltaRate: DataRate

        companion object : FlyWeightId<RxUpdate>
    }

    interface Connect: Notification<Node>, IFW<Connect> {
        var other: Node
        var linkBw: DataRate
        var reapplyRoutingNotifDispenser: FWDispenser<ReapplyRouting>
        var portConnectNotifDispenser: FWDispenser<Port.Connect>

        companion object : FlyWeightId<Connect>
    }

    interface Disconnect: Notification<Node>, IFW<Disconnect> {
        var other: Node
        var portDisconnectNotifDispenser: FWDispenser<Port.Disconnect>
        var reapplyRoutingNotifDispenser: FWDispenser<ReapplyRouting>
        var notifyOther: Boolean

        companion object : FlyWeightId<Disconnect>
    }

    interface ReapplyRouting: Notification<Node>, IFW<ReapplyRouting> {
        var portProcessNotifDispenser: FWDispenser<Port.StartProcessing>

        companion object : FlyWeightId<ReapplyRouting>
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


