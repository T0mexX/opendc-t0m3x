package org.opendc.simulator.network.components.node

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.exchangeRoutVect
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.RoutingVect
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.shareRoutingVect
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.publics.Notification

internal abstract class NodeV0 protected constructor(
    final override val id: NodeId,
) : Node {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val routingTable: RoutingTable = RoutingTable(id)
    override var job: Job? = null

    override suspend fun connectTo(other: Node, linkBw: DataRate) {
        val notif = connectDisp.acquire()
        notif.other = other
        notif.linkBw = linkBw
        _notificationChl.send(notif)
    }

    override suspend fun disconnectFrom(other: Node) {
        val notif = disconnectDisp.acquire()
        notif.other = other
        _notificationChl.send(notif)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private suspend fun awaitPorts() {
        combine(ports.map { it.state }) { states ->
            states.all { it == Port.STABLE || it == Port.IDLE || it == Port.DISCONNECTED }
        }.first { it }
    }

    context(NetSimScope)
    private suspend fun portProcess() {
        ports.forEach {
            it.notificationChl.send(portVersion.startProcessingDisp.acquire())
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope) override fun netLaunch(): Job {
        job = this@NetSimScope.scopeLaunch {
            while (isActive) {
                _notificationChl.receive().handle()
            }
        }
        return job!!
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val notificationChl: SendChannel<Notification<Node>> get() = _notificationChl
    @Suppress("LeakingThis")
    private val _notificationChl: InvalidatorChl<Notification<Node>> = InvalidatorChl(receiver = this)
    override val priorityNotificationChl: SendChannel<Notification<Node>> get() = _priorityNotificationChl
    @Suppress("LeakingThis")
    private val _priorityNotificationChl: InvalidatorChl<Notification<Node>> = InvalidatorChl(receiver = this)

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
            _rxUpdateDisp = dispenser(Node.RxUpdate)
            _connectDisp = dispenser(Node.Connect)
            _disconnectDisp = dispenser(Node.Disconnect)
            _reapplyRoutingDisp = dispenser(Node.ReapplyRouting)
            _acceptConnectioDisp = dispenser(Node.AcceptConnection)
        }

        context(NetSimScope) private suspend fun dispenser(
            id: FWId<Node.RxUpdate>
        ): FWDispenser<Node.RxUpdate> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Node.RxUpdate {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var netFlow: NetFlow
                    override var deltaRate: DataRate = DataRate.zero

                    context(Node)
                    override suspend fun handle() {
                        flowTable.sendToPorts(this)
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(
            id: FWId<Node.Connect>
        ): FWDispenser<Node.Connect> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Node.Connect {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var other: Node
                    override var linkBw = DataRate.zero

                    context(Node)
                    override suspend fun handle() {
                        val n = this@Node as NodeV0
                        val otherN = other as NodeV0
                        val freePort: Port = n.ports.firstOrNull { it.txLink == null }!!

                        //
                        n.awaitPorts()

                        // Ask another node to accept connection and retrieve its port.
                        val answNotif: Node.AcceptConnection = nodeVersion.acceptConnectionDisp.acquire()
                        answNotif.toBeAccepted = freePort
                        otherN.notificationChl.send(answNotif)
                        val otherPort = answNotif.answerChl.receive()

                        // Dispose of the "flyweight" `AnsweredNotification` after receiving answer.
                        answNotif.dispose()

                        // Ask the port on this node to connect to the port on the other node.
                        val notif = portVersion.connectDisp.acquire()
                        notif.linkBw = this.linkBw
                        notif.other = otherPort
                        freePort.notificationChl.send(notif)

                        // Update routing tables of all nodes.
                        val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.getVect(), vectOwner = n)
                        routingTable.mergeRoutingVector(otherVect, vectOwner = other)
                        shareRoutingVect(except = listOf(other))

                        // Reapply routing after routing table update.
                        n.priorityNotificationChl.send(reapplyRoutingDisp.acquire())

                        // Dispose of the "flyweight" notification received.
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(
            id: FWId<Node.Disconnect>
        ): FWDispenser<Node.Disconnect> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val portDisconnectDisp = portVersion.disconnectDisp
                val nodeReapplyRoutingDisp = reapplyRoutingDisp
                object : Node.Disconnect {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var other: Node
                    override var notifyOther = false

                    context(Node)
                    override suspend fun handle() {
                        val n = this@Node as NodeV0
                        val otherN = other as NodeV0
                        val portToOther: Port = n.ports.firstOrNull { it.txLink?.receiverPort?.owner === otherN }!!

                        if (notifyOther) {
                            val notif = pool.dispenser().acquire()
                            notif.other = n
                            notif.notifyOther = false
                            other.priorityNotificationChl.send(notif)
                        }

                        val notif = portDisconnectDisp.acquire()
                        portToOther.priorityNotificationChl.send(notif)

                        routingTable.removeNextHop(other)
                        shareRoutingVect(exchange = true)
                        n.priorityNotificationChl.send(nodeReapplyRoutingDisp.acquire())

                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(
            id: FWId<Node.ReapplyRouting>
        ): FWDispenser<Node.ReapplyRouting> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Node.ReapplyRouting {
                    override val pool = pool
                    override val poolIdx = idx

                    context(Node)
                    override suspend fun handle() {
                        val n = this@Node as NodeV0

                        n.flowTable.reapplyRouting()

                        n.ports.forEach {
                            it.priorityNotificationChl.send(portVersion.startProcessingDisp.acquire())
                        }

                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(
            id: FWId<Node.AcceptConnection>
        ): FWDispenser<Node.AcceptConnection> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Node.AcceptConnection {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var toBeAccepted: Port
                    override val answerChl = Channel<Port>()
                    override var linkBw: DataRate = DataRate.zero

                    context(Node)
                    override suspend fun handle() {
                        val n = this@Node as NodeV0

                        n.portProcess()
                        n.awaitPorts()

                        val freePort: Port = ports.find { it.txLink == null }!!
                        val notif = portVersion.connectDisp.acquire()
                        notif.other = toBeAccepted
                        notif.linkBw = linkBw
                        freePort.priorityNotificationChl.send(notif)
                        n.awaitPorts()

                        answerChl.send(freePort)

                        dispose()
                    }
                }
            }.dispenser()
    }
}
