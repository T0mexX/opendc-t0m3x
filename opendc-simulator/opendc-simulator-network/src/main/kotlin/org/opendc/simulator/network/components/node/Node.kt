/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NetCoOwner
import org.opendc.simulator.network.components.NetRunnable
import org.opendc.simulator.network.components.NonOwnerMethod
import org.opendc.simulator.network.components.NonOwnerProperty
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.components.invalidatable.IInvalidatable
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.Msgable
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.TopNodeMeta
import org.opendc.simulator.network.components.node.internalstructs.flowtable.FlowTable
import org.opendc.simulator.network.components.node.internalstructs.routtbl.RoutTblImpl
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.fwpool.FW
import org.opendc.simulator.network.simscope.fwpool.FWId

/**
 * Interface representing a node in a [Network].
 *
 * @param Self The more specific type of this [Node].
 */
@NetCoOwner(owner = NetCo.NODE)
internal interface Node<Self : Node<Self>> : IInvalidatable, Msgable<Node<*>>, NetRunnable, EnConsumer<Self> {
    /**
     * The ip address associated with this node. All nodes have a unique ip address, including switches.
     */
    val ip: IPv4Address

    /**
     * ID of the node. Uniquely identifies the node in the [Network].
     */
    val id: NodeId
        get() = NodeId(ip.longValue().toUInt())

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
     * @see TopNodeMeta
     */
    @NonOwnerProperty(readableBy = [NetCo.MAIN])
    var topNodeMeta: TopNodeMeta<*>?

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routTbl: RoutTblImpl

    /**
     * Contains information about the [NetFlow]s transiting through this node.
     */
    val flowTbl: FlowTable

    /**
     * @return The index of the first element in [links] that is `null`,
     * corresponding to an unused port.
     */
    fun getFreeLinkIdx(): Int

    /**
     * TODO
     */
    @NonOwnerMethod(callableBy = [NetCo.MAIN])
    fun toSpecs(): NodeSpecs<Self>

    /**
     * Used especially for assertions disabled at compile time.
     */
    @NonOwnerMethod(callableBy = [NetCo.MAIN])
    infix fun isConnectedTo(otherN: Node<*>): Boolean = this.links.any { it?.receiverN === otherN }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Convenience Message Methods
    // //// Used for convenience instead of manually acquire, fill, and send flyweight message objects.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Convenience method to send a [RxUpdt]~[Msg] to this node.
     * @see RxUpdt
     */
    @NonOwnerMethod(
        callableBy = [NetCo.NODE, NetCo.FLOW],
        additionalInfo =
            "Invoked by flow coroutine when this node is the source," +
                "invoked by an adjacent node coroutine when receiving an update from that node",
    )
    suspend fun msgAsyncRxUpdt(
        deltaRate: DataRate,
        f: INetFlow,
    )

    /**
     * Convenience method to send a [Connect]~[Msg] to this node.
     * @see Connect
     */
    @NonOwnerMethod(callableBy = [NetCo.MAIN])
    suspend fun msgSyncConnect(
        other: Node<*>,
        linkBw: DataRate = this.portSpeed min other.portSpeed,
        updtRoutTbl: Boolean = true,
    )

    /**
     * Convenience method to send a [Disconnect]~[Msg] to this node.
     * @see RxUpdt
     */
    @NonOwnerMethod(callableBy = [NetCo.MAIN])
    suspend fun msgSyncDisconnect(other: Node<*>)

    /**
     * Convenience method to send a [ShareRoutVect]~[Msg] to this node.
     */
    @NonOwnerMethod(callableBy = [NetCo.MAIN])
    suspend fun msgAsyncShareRoutVect()

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Messages
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    interface StartFlow : Msg<Node<*>, StartFlow> {
        // The original demand of the flow, [f.demand] may have been changed in the meantime,
        // and that change will be received as a [Node.RxUpdate] msg.
        var ogDmnd: DataRate
        var f: INetFlow

        companion object : FWId<StartFlow>
    }

    interface StopFlow : Msg<Node<*>, StopFlow> {
        var f: INetFlow

        companion object : FWId<StopFlow>
    }

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
    interface RxUpdt : Msg<Node<*>, RxUpdt> {
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
    interface Connect : Msg<Node<*>, Connect> {
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
    interface AcceptConnection : Msg<Node<*>, AcceptConnection> {
        var toBeAccepted: Node<*>
        var updtRoutTbl: Boolean

        companion object : FWId<AcceptConnection>
    }

    /**
     * TODO: Not used after total refactor.
     */
    interface Disconnect : Msg<Node<*>, Disconnect> {
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
    interface ApplyRouting : Msg<Node<*>, ApplyRouting> {
        companion object : FWId<ApplyRouting>
    }

    /**
     * Represents a routing table update sent from [from] with new information,
     * so that the receiver [Node] can update its own.
     *
     * @property from The adjacent node the routing update is coming from.
     * @property routVect The routing vector of [from] node.
     */
    interface RoutTblUpdt : Msg<Node<*>, RoutTblUpdt> {
        var from: Node<*>
        var routVect: RoutTblImpl.RoutVect

        companion object : FWId<RoutTblUpdt>
    }

    /**
     * Commands the receiver [Node] to send a [RoutTblUpdt] to all adjacent connected [Node]s.
     */
    interface ShareRoutVect : Msg<Node<*>, ShareRoutVect> {
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
