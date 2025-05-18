package org.opendc.simulator.network.components.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope
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
    override lateinit var job: Job

    override suspend fun msgAsyncRxUpdt(deltaRate: DataRate, netF: INetFlow) {
        assert(deltaRate.approx(DataRate.zero).not()) {deltaRate}

        val msg = rxUpdateDisp.acquire().reset()
        msg.deltaRate = deltaRate
        msg.netF = netF
        msg.sendTo(this)
    }

    override suspend fun msgSyncConnect(other: Node<*>, linkBw: DataRate) {
        val msg = connectDisp.acquire().reset()
        msg.other = other
        msg.linkBw = linkBw
        msg.sendTo(this, dispose = false).awaitHandling().dispose()
    }

    override suspend fun msgSyncDisconnect(other: Node<*>) {
        val notif = disconnectDisp.acquire().reset()
        notif.other = other
        notif.sendTo(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Awaits all ports to be in [Port.STABLE] or [Port.DISCONNECTED] or [Port.IDLE] states.
     */
    private suspend fun awaitPorts() {
        if (ports.isEmpty()) return
        combine(ports.map { it.state }) { states ->
            states.all { it == Port.STABLE || it == Port.IDLE || it == Port.DISCONNECTED }
        }.first { it }
    }

    /**
     * @return One free port (not connected) available on this node.
     */
    context(NetSimScope)
    protected open suspend fun getFreePort(): Port? = ports.firstOrNull { it.txLink == null }

    /**
     * Sends [Port.Process] msgs to all ports on the node and awaits the ports to be done processing the updates.
     */
    context(NetSimScope)
    protected suspend fun portProcessAwait() {
        coroutineScope {
            ports.asFlow().onEach { p ->
                // TODO: change to use `Port` convenience methods.
                portVersion
                    .startProcessingDisp
                    .acquire()
                    .reset()
                    .sendTo(p, dispose = false)
                    .awaitHandling()
                    .dispose()
            }.launchIn(this@coroutineScope)
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope)
    override fun netLaunch(scope: CoroutineScope): Job {
        assert(::job.isInitialized.not())

        job = scope.launch {
            // Launch all ports coroutines.
            ports.forEach { it.netLaunch() }

            while (isActive) {
                while (true) {
                    // Accumulate multiple updates if possible
                    // before telling the ports to process them and propagate results.
                    _msgChl.tryReceiveValidate().getOrNull()?.handle()
                        ?: break
                }
                // Make ports process updates and propagate results.
                portProcessAwait()
                // Suspending receive. When node suspends here, its stability is validated.
                _msgChl.receive().handle()
                assert(stabilizer.isValidated.not())
            }
        }

        return job
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Msgable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Suppress("LeakingThis")
    private val _msgChl: InvalidatorChl<Msg<Node<*>, *>> = InvalidatorChl(receiver = this)
    override val msgChl: SendChannel<Msg<Node<*>, *>> get() = _msgChl

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NodeVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V0")
    companion object : NodeVersion {

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Msgs
        ////// Dispenser initialization for flyweight `Msg` objects related to `Node`s
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        override val rxUpdateDisp: FWDispenser<Node.RxUpdt> get() = _rxUpdateDisp
        private lateinit var _rxUpdateDisp: FWDispenser<Node.RxUpdt>

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
                poolAggr.getOrAdd(Node.RxUpdt as FWId<Node.RxUpdt>) { pool, idx ->
                    /**
                     * Anonymous implementation of [Node.RxUpdt].
                     */
                    object : Node.RxUpdt, MsgImpl<Node<*>, Node.RxUpdt>(pool, idx) {
                        override lateinit var netF: INetFlow
                        override var deltaRate: DataRate = DataRate.zero

                        context(Node<*>)
                        override suspend fun handle() {
                            assert(deltaRate.approx(DataRate.zero).not())
                            flowTable.rxUpdt(this)
                            handled()
                        }
                    }
                }.dispenser()



            _connectDisp =
                poolAggr.getOrAdd(Node.Connect as FWId<Node.Connect>) { pool, idx ->
                    /**
                     * Anonymous implementation of [Node.Connect].
                     */
                    object : Node.Connect, MsgImpl<Node<*>, Node.Connect>(pool, idx) {
                        override lateinit var other: Node<*>
                        override var linkBw = DataRate.zero

                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl
                            val otherN = other as NodeImpl
                            val freePort: Port = n.getFreePort() ?: error("unable to connect node, no port available")

                            // Ask another node to accept connection and retrieve its port.
                            val reqMsg = nodeVersion.acceptConnectionDisp.acquire().reset()
                            reqMsg.linkBw = linkBw
                            reqMsg.toBeAccepted = freePort
                            val otherPort = reqMsg.sendTo(otherN).awaitResponse()

                            // Dispose of the "flyweight" `ReqMsg` after receiving response.
                            reqMsg.dispose()

                            // Ask the port on this node to connect to the port on the other node.
                            val msg = portVersion.connectDisp.acquire().reset()
                            msg.linkBw = this.linkBw
                            msg.other = otherPort
                            msg.sendTo(freePort, dispose = false).awaitHandling().dispose()

                            // Update routing tables of all nodes. TODO: change
                            val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.getVect(), vectOwner = n)
                            routingTable.mergeRoutingVector(otherVect, vectOwner = other)
                            shareRoutingVect(except = listOf(other))

                            // Reapply routing after routing table update.
                            reapplyRoutingDisp.acquire().reset().handle()

                            handled()
                        }
                }
            }.dispenser()



            _disconnectDisp =
                poolAggr.getOrAdd(Node.Disconnect as FWId<Node.Disconnect>) { pool, idx ->
                    val portDisconnectDisp = portVersion.disconnectDisp
                    /**
                     * Anonymous implementation of [Node.Disconnect].
                     */
                    object : Node.Disconnect, MsgImpl<Node<*>, Node.Disconnect>(pool, idx) {
                        override lateinit var other: Node<*>
                        override var notifyOther = false

                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl
                            val otherN = other as NodeImpl
                            val portToOther: Port = n.ports.firstOrNull { it.txLink?.receiverPort?.owner === otherN }!!

                            if (notifyOther) {
                                val msg = pool.dispenser().acquire().reset()
                                msg.other = n
                                msg.notifyOther = false
                                msg.sendTo(other)
                            }

                            val msg = portDisconnectDisp.acquire().reset()
                            msg.sendTo(portToOther)

                            routingTable.removeNextHop(other)
                            shareRoutingVect(exchange = true)
                            reapplyRoutingDisp.acquire().reset().sendTo(n)

                            handled()
                        }
                    }
                }.dispenser()



            _reapplyRoutingDisp =
                poolAggr.getOrAdd(Node.ReapplyRouting as FWId<Node.ReapplyRouting>) { pool, idx ->
                    /**
                     * Anonymous implementation of [Node.ReapplyRouting].
                     */
                    object : Node.ReapplyRouting, MsgImpl<Node<*>, Node.ReapplyRouting>(pool, idx) {
                        context(Node<*>)
                        override suspend fun handle() {
                            val n = this@Node as NodeImpl

                            n.flowTable.reapplyRouting()

                            // Make ports reapply fairness policy.
                            n.portProcessAwait()

                            handled()
                        }
                    }
                }.dispenser()



            _acceptConnectioDisp = poolAggr.getOrAdd(Node.AcceptConnection as FWId<Node.AcceptConnection>) { pool, idx ->
                /**
                 * Anonymous implementation of [Node.AcceptConnection].
                 */
                object : Node.AcceptConnection, ReqMsgImpl<Node<*>, Port, Node.AcceptConnection>(pool, idx) {
                    override lateinit var toBeAccepted: Port
                    override var linkBw: DataRate = DataRate.zero

                    context(Node<*>)
                    override suspend fun handle() {
                        val n = this@Node as NodeImpl<*>

                        n.portProcessAwait()
                        n.awaitPorts()

                        val freePort: Port = n.getFreePort()
                            ?: let {
                                n
                                error("unable to connect node, no port available")
                            }
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
