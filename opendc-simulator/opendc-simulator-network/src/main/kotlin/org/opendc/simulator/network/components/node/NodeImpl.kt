package org.opendc.simulator.network.components.node

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.exchangeRoutVect
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.RoutingVect
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.shareRoutingVect
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.ReqMsgImpl
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.MsgImpl

internal abstract class NodeImpl<Self: Node<Self>> protected constructor(
    final override val id: NodeId,
) : Node<Self> {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val routingTable: RoutingTable = RoutingTable(id)
    override var job: Job? = null

    override suspend fun msgAsyncRxUpdt(deltaRate: DataRate, netF: INetFlow) {
        val msg = rxUpdateDisp.acquire().reset()
        msg.deltaRate = deltaRate
        msg.netF = netF
        msg.sendTo(this)
    }

    override suspend fun connectTo(other: Node<*>, linkBw: DataRate) {
        val msg = connectDisp.acquire().reset()
        msg.other = other
        msg.linkBw = linkBw
        msg.sendTo(this, dispose = false).awaitHandling().dispose()
    }

    override suspend fun disconnectFrom(other: Node<*>) {
        val notif = disconnectDisp.acquire().reset()
        notif.other = other
        _notificationChl.send(notif)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private suspend fun awaitPorts() {
        if (ports.isEmpty()) return
        combine(ports.map { it.state }) { states ->
            states.all { it == Port.STABLE || it == Port.IDLE || it == Port.DISCONNECTED }
        }.first { it }
    }

    /**
     * TODO
     */
    context(NetSimScope)
    protected open suspend fun getFreePort(): Port = ports.first { it.txLink == null }

    /**
     * TODO
     * this awaits for process to finish
     */
    context(NetSimScope)
    protected suspend fun portProcessAwait() {
        coroutineScope {
            ports.asFlow().onEach { p ->
                portVersion
                    .startProcessingDisp
                    .acquire()
                    .reset()
                    .sendToPrioritized(p, dispose = false)
                    .awaitHandling()
                    .dispose()
            }.launchIn(this)
        }
//        ports.map { p ->
//            portVersion
//                .startProcessingDisp
//                .acquire()
//                .reset()
//                .sendToPrioritized(p, dispose = false)
//        }.forEach {
//            it.awaitHandling().dispose()
//        }
//
//        coroutineScope {
//            ports.asFlow().onEach { p ->
//                portVersion
//                    .startProcessingDisp
//                    .acquire()
//                    .reset()
//                    .sendToPrioritized(p, dispose = false)
//                    .awaitHandling()
//                    .dispose()
//            }.launchIn(this)
//        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope) override fun netLaunch(): Job {
        job = this@NetSimScope.scopeLaunch {
            ports.forEach { it.netLaunch() }
            while (isActive) {
                _notificationChl.receive().handle()
            }
        }
        return job!!
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    override val msgChl: SendChannel<Msg<Node<*>, *>> get() = _notificationChl
    @Suppress("LeakingThis")
    private val _notificationChl: InvalidatorChl<Msg<Node<*>, *>> = InvalidatorChl(receiver = this)

    override val priorityMsgChl: SendChannel<Msg<Node<*>, *>> get() = _priorityNotificationChl
    @Suppress("LeakingThis")
    private val _priorityNotificationChl: InvalidatorChl<Msg<Node<*>, *>> = InvalidatorChl(receiver = this)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NodeVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V0")
    companion object : NodeVersion {

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        override val rxUpdateDisp: FWDispenser<Node.RxUpdate> get() = _rxUpdateDisp
        private lateinit var _rxUpdateDisp: FWDispenser<Node.RxUpdate>

        override val connectDisp: FWDispenser<Node.Connect> get() = _connectDisp
        private lateinit var _connectDisp: FWDispenser<Node.Connect>

        override val disconnectDisp: FWDispenser<Node.Disconnect> get() = _disconnectDisp
        private lateinit var _disconnectDisp: FWDispenser<Node.Disconnect>

        override val reapplyRoutingDisp: FWDispenser<Node.ReapplyRouting> get() = _reapplyRoutingDisp
        private lateinit var _reapplyRoutingDisp: FWDispenser<Node.ReapplyRouting>

        override val acceptConnectionDisp: FWDispenser<Node.AcceptConnection> get() = _acceptConnectioDisp
        private lateinit var _acceptConnectioDisp: FWDispenser<Node.AcceptConnection>


        context(NetSimScope) override suspend fun initDispensers() {
            _rxUpdateDisp =
                poolAggr.getOrAdd(Node.RxUpdate as FWId<Node.RxUpdate>) { pool, idx ->
                    object : Node.RxUpdate, MsgImpl<Node<*>, Node.RxUpdate>() {
                        override val pool = pool
                        override val poolIdx = idx
                        override lateinit var netF: INetFlow
                        override var deltaRate: DataRate = DataRate.zero

                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl
                            flowTable.rxUpdt(this)
                            // TODO: change
                            n.portProcessAwait()
                            handled()
                        }
                    }
                }.dispenser()



            _connectDisp =
                poolAggr.getOrAdd(Node.Connect as FWId<Node.Connect>) { pool, idx ->
                    object : Node.Connect, MsgImpl<Node<*>, Node.Connect>() {
                        override val pool = pool
                        override val poolIdx = idx
                        override lateinit var other: Node<*>
                        override var linkBw = DataRate.zero

                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl
                            val otherN = other as NodeImpl
                            val freePort: Port = n.getFreePort()

                            //
                            n.awaitPorts()

                            // Ask another node to accept connection and retrieve its port.
                            val reqMsg = nodeVersion.acceptConnectionDisp.acquire().reset()
                            reqMsg.linkBw = linkBw
                            reqMsg.toBeAccepted = freePort
                            val otherPort = reqMsg.sendTo(otherN).awaitResponse()

                            // Dispose of the "flyweight" `AnsweredNotification` after receiving answer.
                            reqMsg.dispose()

                            // Ask the port on this node to connect to the port on the other node.
                            val msg = portVersion.connectDisp.acquire().reset()
                            msg.linkBw = this.linkBw
                            msg.other = otherPort
                            msg.sendTo(freePort, dispose = false).awaitHandling().dispose()

                            // Update routing tables of all nodes.
                            val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.getVect(), vectOwner = n)
                            routingTable.mergeRoutingVector(otherVect, vectOwner = other)
                            shareRoutingVect(except = listOf(other))

                            // Reapply routing after routing table update.
                            reapplyRoutingDisp.acquire().reset().handle()
//                            reapplyRoutingDisp.acquire().sendToPrioritized(n, dispose = false).awaitHandling().dispose()

                            handled()
                        }
                }
            }.dispenser()



            _disconnectDisp =
                poolAggr.getOrAdd(Node.Disconnect as FWId<Node.Disconnect>) { pool, idx ->
                    val portDisconnectDisp = portVersion.disconnectDisp
                    val nodeReapplyRoutingDisp = reapplyRoutingDisp
                    object : Node.Disconnect, MsgImpl<Node<*>, Node.Disconnect>() {
                        override val pool = pool
                        override val poolIdx = idx
                        override lateinit var other: Node<*>
                        override var notifyOther = false

                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl
                            val otherN = other as NodeImpl
                            val portToOther: Port = n.ports.firstOrNull { it.txLink?.receiverPort?.owner === otherN }!!

                            if (notifyOther) {
                                val notif = pool.dispenser().acquire()
                                notif.other = n
                                notif.notifyOther = false
                                other.priorityMsgChl.send(notif)
                            }

                            val notif = portDisconnectDisp.acquire()
                            portToOther.priorityMsgChl.send(notif)

                            routingTable.removeNextHop(other)
                            shareRoutingVect(exchange = true)
                            n.priorityMsgChl.send(nodeReapplyRoutingDisp.acquire())

                            handled()
                        }
                    }
                }.dispenser()



            _reapplyRoutingDisp =
                poolAggr.getOrAdd(Node.ReapplyRouting as FWId<Node.ReapplyRouting>) { pool, idx ->
                    object : Node.ReapplyRouting, MsgImpl<Node<*>, Node.ReapplyRouting>() {
                        override val pool = pool
                        override val poolIdx = idx

                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl

                            n.flowTable.reapplyRouting()

                            // Make ports reapply fairness policy
                            n.portProcessAwait()
//                            coroutineScope {
//                                n.ports.asFlow().onEach { p ->
//                                    portVersion
//                                        .startProcessingDisp
//                                        .acquire()
//                                        .reset()
//                                        .sendToPrioritized(p, dispose = false)
//                                        .awaitHandling()
//                                        .dispose()
//                                }.launchIn(this)
//                            }
//                            n.ports.map {
//                                portVersion.startProcessingDisp.acquire().sendToPrioritized(it, dispose = false)
////                                it.priorityMsgChl.send(portVersion.startProcessingDisp.acquire())
//                            }.map { it.awaitHandling().dispose() }

                            handled()
                        }
                    }
                }.dispenser()



            _acceptConnectioDisp = poolAggr.getOrAdd(Node.AcceptConnection as FWId<Node.AcceptConnection>) { pool, idx ->
                object : Node.AcceptConnection, ReqMsgImpl<Node<*>, Port, Node.AcceptConnection>() {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var toBeAccepted: Port
                    override var linkBw: DataRate = DataRate.zero

                    context(Node<*>)
                    override suspend fun handle() {
                        val n = this@Node as NodeImpl<*>

                        n.portProcessAwait()
                        n.awaitPorts()

                        val freePort: Port = n.getFreePort()
                        val msg = portVersion.connectDisp.acquire().reset()
                        msg.other = toBeAccepted
                        msg.linkBw = linkBw
                        msg.sendTo(freePort, dispose = false).awaitHandling().dispose()

                        respond(freePort)
                    }
                }
            }.dispenser()
        }
    }
}
