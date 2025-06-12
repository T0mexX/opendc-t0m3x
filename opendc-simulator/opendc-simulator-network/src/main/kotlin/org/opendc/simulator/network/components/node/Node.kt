package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.Job
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.internalstructs.RoutTbl2
import org.opendc.simulator.network.components.specs.WithSpecs
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.TopNodeMeta
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.Msgable


/**
 * Interface representing a node in a [Network].
 *
 * @param Self The more specific type of this [Node].
 */
internal interface Node<Self: Node<Self>> : WithSpecs<SerializableNode>, IInvalidatable, Msgable<Node<*>>, Launchable, EnConsumer<Self> {
    /**
     * The ip address associated with this node. All nodes have a unique ip address, including switches.
     */
    val ip: IPv4Address

    /**
     * ID of the node. Uniquely identifies the node in the [Network].
     */
    val id: NodeId
        get() = NodeId(ip.intValue().toLong())

    /**
     * Port speed in Kbps full duplex.
     */
    val portSpeed: DataRate

    /**
     * Number of ports of ***this*** [Node].
     */
    val nPorts: Int

    /**
     * List of links available on this node.
     */
    val links: List<Link?>

    /**
     * The coroutine's job that is running this node's logic and processing incoming messages.
     */
    val job: Job?

    /**
     * Policy that determines to which [Port]s the flowsById are forwarded to.
     */
    val routPolicy: RoutPolicy

    /**
     * TODO
     */
    var topNodeMeta: TopNodeMeta<*>?

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routTbl: RoutTbl2

    /**
     * Contains information about the [NetFlow]s transiting through this node.
     */
    val flowTable: FlowTable

    fun getFreeLinkIdx(): Int = links.indexOfFirst { it == null }.takeIf { it != -1 } ?: let {
        error("port not available")
    }

    /**
     * Convenience method to send a [RxUpdt] [Msg] to this node.
     * @see RxUpdt
     */
    suspend fun msgAsyncRxUpdt(deltaRate: DataRate, f: INetFlow)

    /**
     * Convenience method to send a [Connect] [Msg] to this node.
     * @see Connect
     */
    suspend fun msgSyncConnect(
        other: Node<*>,
        linkBw: DataRate = this.portSpeed min other.portSpeed,
        updtRoutTbl: Boolean = true,
    )

    /**
     * Convenience method to send a [Disconnect] [Msg] to this node.
     * @see RxUpdt
     */
    suspend fun msgSyncDisconnect(other: Node<*>)

    /**
     * TODO
     */
    suspend fun msgAsyncShareRoutVect()

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Messages
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A [Msg] that informs the receiving node that the incoming data rate for flow [netF]
     * has changed by [deltaRate].
     *
     * This message is used to propagate bandwidth updates and is processed to adjust the
     * node’s internal state accordingly.
     *
     * For details about inter-component communication using messages, see [Msg].
     * For an explanation of flyweight objects used during simulation, see [FW].
     */
    interface RxUpdt: Msg<Node<*>, RxUpdt> {
        var netF: INetFlow
        var deltaRate: DataRate
        var toIntermediate: Boolean

        companion object : FWId<RxUpdt>
    }

    /**
     * A [Msg] instructing the receiving node to initiate a connection to [other]
     * using a link with the specified bandwidth capacity [linkBw].
     *
     * TODO
     * This msg can be used for dynamic link establishment during simulation, especially in REPL environment.
     *
     * For details about inter-component communication using messages, see [Msg].
     * For an explanation of flyweight objects used during simulation, see [FW].
     */
    interface Connect: Msg<Node<*>, Connect> {
        var other: Node<*>
        var updtRoutTbl: Boolean

        companion object : FWId<Connect>
    }

    /**
     * A [Msg] instructing the receiving node to accept connection from [toBeAccepted] port and its owner node,
     * using a link with the specified bandwidth capacity [linkBw].
     *
     * This msg is sent by the [Node] that initiate the connection.
     *
     * This msg can be used for dynamic link establishment during simulation, especially in REPL environment.
     *
     * For details about inter-component communication using messages, see [Msg].
     * For an explanation of flyweight objects used during simulation, see [FW].
     * @see Connect
     */
    interface AcceptConnection: Msg<Node<*>, AcceptConnection> {
        var toBeAccepted: Node<*>
        var updtRoutTbl: Boolean

        companion object : FWId<AcceptConnection>
    }

    /**
     * TODO: Not used after total refactor.
     */
    interface Disconnect: Msg<Node<*>, Disconnect> {
        var other: Node<*>
        var notifyOther: Boolean

        companion object : FWId<Disconnect>
    }

    /**
     * A [Msg] instructing the receiving node to reapply [Node.routPolicy]~[RoutPolicy].
     * Typically, triggered internally upon new connection/disconnection,
     * or externally in case of a global routing policy.
     *
     * For details about inter-component communication using messages, see [Msg].
     * For an explanation of flyweight objects used during simulation, see [FW].
     */
    interface ApplyRouting: Msg<Node<*>, ApplyRouting> {

        companion object : FWId<ApplyRouting>
    }

    /**
     * TODO
     * @property from The adjacent node the routing update is coming from.
     * @property routVect The routing vector of [from] node.
     */
    interface RoutTblUpdt: Msg<Node<*>, RoutTblUpdt> {
        var from: Node<*>
        var routVect: RoutTbl2.RoutVect

        companion object : FWId<RoutTblUpdt>
    }

    /**
     * TODO
     */
    interface ShareRoutVect: Msg<Node<*>, ShareRoutVect> {

        companion object : FWId<ShareRoutVect>
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


