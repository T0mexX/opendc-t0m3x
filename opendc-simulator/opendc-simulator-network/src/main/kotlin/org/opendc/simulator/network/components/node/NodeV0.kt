package org.opendc.simulator.network.components.node

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.exchangeRoutVect
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.RoutingVect
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.shareRoutingVect
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.publics.Notification

internal abstract class NodeV0 protected constructor(
    final override val id: NodeId,
) : Node {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val routingTable: RoutingTable = RoutingTable(id)

    override suspend fun connectTo(other: Node, linkBw: DataRate) {
        TODO("Not yet implemented")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope) override fun netLaunch(): Job = this@NetSimScope.scopeLaunch {
        while (isActive) {
            acceptMtx.withLock {
                notificationChl.receive().handle()
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val notificationChl: SendChannel<Notification<Node>> get() = _notificationChl
    @Suppress("LeakingThis")
    protected val _notificationChl: InvalidatorChl<Notification<Node>> = InvalidatorChl(receiver = this)
    override val priorityNotificationChl: SendChannel<Notification<Node>> get() = _priorityNotificationChl
    @Suppress("LeakingThis")
    protected val _priorityNotificationChl: InvalidatorChl<Notification<Node>> = InvalidatorChl(receiver = this)


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Impl
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val acceptMtx = Mutex()
    private suspend fun accept(n: Node): Port {
        acceptMtx.withLock {
            TODO()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NodeVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V0")
    companion object : NodeVersion {

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        context(NetSimScope) override suspend fun dispenser(
            id: FlyWeightId<Node.RxUpdate>
        ): FWDispenser<Node.RxUpdate> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Node.RxUpdate {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var netFlow: NetFlow
                    override var deltaRate: DataRate = DataRate.zero

                    context(Node)
                    override suspend fun handle() {
                        TODO("Not yet implemented")
                    }
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FlyWeightId<Node.Connect>
        ): FWDispenser<Node.Connect> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val disp1 = devConfig.portConfig.version.dispenser(Port.Connect)
                val disp2 = dispenser(Node.ReapplyRouting)
                object : Node.Connect {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var other: Node
                    override var linkBw = DataRate.zero
                    override var portConnectNotifDispenser = disp1
                    override var reapplyRoutingNotifDispenser = disp2

                    context(Node)
                    override suspend fun handle() {
                        val n = this@Node as NodeV0
                        val otherN = other as NodeV0
                        val freePort: Port = n.ports.firstOrNull { it.txLink == null }!!

                        val otherPort: Port = otherN.accept(n)
                        val notif = portConnectNotifDispenser.acquire()
                        notif.linkBw = this.linkBw
                        notif.notifyOther = true
                        notif.other = otherPort
                        freePort.notificationChl.send(notif)

                        val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.getVect(), vectOwner = n)
                        routingTable.mergeRoutingVector(otherVect, vectOwner = other)
                        shareRoutingVect(except = listOf(other))

                        n.priorityNotificationChl.send(reapplyRoutingNotifDispenser.acquire())

                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FlyWeightId<Node.Disconnect>
        ): FWDispenser<Node.Disconnect> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val disp1 = devConfig.portConfig.version.dispenser(Port.Disconnect)
                val disp2 = dispenser(Node.ReapplyRouting)
                object : Node.Disconnect {
                    override val pool = pool
                    override val poolIdx = idx
                    override lateinit var other: Node
                    override var portDisconnectNotifDispenser = disp1
                    override var reapplyRoutingNotifDispenser = disp2
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

                        val notif = portDisconnectNotifDispenser.acquire()
                        portToOther.priorityNotificationChl.send(notif)

                        routingTable.removeNextHop(other)
                        shareRoutingVect(exchange = true)
                        n.priorityNotificationChl.send(reapplyRoutingNotifDispenser.acquire())

                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FlyWeightId<Node.ReapplyRouting>
        ): FWDispenser<Node.ReapplyRouting> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val disp1 = devConfig.portConfig.version.dispenser(Port.StartProcessing)
                object : Node.ReapplyRouting {
                    override val pool = pool
                    override val poolIdx = idx
                    override var portProcessNotifDispenser = disp1

                    context(Node)
                    override suspend fun handle() {
                        val n = this@Node as NodeV0
                        TODO()

                        n.ports.forEach {
                            it.priorityNotificationChl.send(portProcessNotifDispenser.acquire())
                        }

                        dispose()
                    }
                }
            }.dispenser()
    }
}
