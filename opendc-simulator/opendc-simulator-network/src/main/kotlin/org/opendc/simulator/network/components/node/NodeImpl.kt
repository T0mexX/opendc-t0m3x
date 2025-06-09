package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.internalstructs.RoutTbl2
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.link.LinkImpl
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.MsgImpl

internal abstract class NodeImpl<Self: Node<Self>> protected constructor(
    final override val ip: IPv4Address,
    nPorts: Int,
) : Node<Self> {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val links: MutableList<Link?> = ArrayList<Link?>(nPorts).also { l ->
        repeat(nPorts) { l.add(null) }
    }

    @Suppress("LeakingThis")
    override val routTbl: RoutTbl2 = RoutTbl2(owner = this)
    override lateinit var job: Job

    override suspend fun msgAsyncRxUpdt(deltaRate: DataRate, f: INetFlow) {
        assert(deltaRate.approx(DataRate.zero).not()) {deltaRate}

        rxUpdateDisp.acquire().reset {
            this.deltaRate = deltaRate
            this.netF = f
        }.sendTo(this)
    }

    override suspend fun msgSyncConnect(other: Node<*>, linkBw: DataRate, updtRoutTbl: Boolean) {
        val msg = connectDisp.acquire().reset()
        msg.other = other
        msg.updtRoutTbl = updtRoutTbl
        msg.sendTo(this, dispose = false).awaitHandling().dispose()
    }

    override suspend fun msgSyncDisconnect(other: Node<*>) {
        val notif = disconnectDisp.acquire().reset()
        notif.other = other
        notif.sendTo(this)
    }

    override suspend fun msgAsyncShareRoutVect() {
        shareRoutVectDisp.acquire().reset().sendTo(this)
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//    /**
//     * Awaits all ports to be in [Port.STABLE] or [Port.DISCONNECTED] or [Port.IDLE] states.
//     */
//    private suspend fun awaitPorts() {
//        if (ports.isEmpty()) return
//        combine(ports.map { it.state }) { states ->
//            states.all { it == Port.STABLE || it == Port.IDLE || it == Port.DISCONNECTED }
//        }.first { it }
//    }

//    /**
//     * @return One free port (not connected) available on this node.
//     */
//    context(NetSimScope)
//    protected open suspend fun getFreePort(): Port? = ports.firstOrNull { it.txLink == null }

//    /**
//     * Sends [Port.Process] msgs to all ports on the node and awaits the ports to be done processing the updates.
//     */
//    context(NetSimScope)
//    protected suspend fun portProcessAwait() {
//        coroutineScope {
//            ports.asFlow().onEach { p ->
//                // TODO: change to use `Port` convenience methods.
//                portVersion
//                    .startProcessingDisp
//                    .acquire()
//                    .reset()
//                    .sendTo(p, dispose = false)
//                    .awaitHandling()
//                    .dispose()
//            }.launchIn(this@coroutineScope)
//        }
//    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope)
    override suspend fun netLaunch(scope: CoroutineScope): Job {
        assert(::job.isInitialized.not())

        job = scope.launch(CoroutineID.new()) {
            while (isActive) {
                while (true) {
                    // Accumulate multiple updates if possible
                    // before telling the ports to process them and propagate results.
                    _msgChl.tryReceiveValidate().getOrNull()?.handle()
                        ?: break
                }
                //
                // Propagate updates to adjacent nodes.
                coroutineScope {
                    links.forEach { l ->
                        launch { l?.attemptTx() }
                    }
                }
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

        override val applyRoutingDisp: FWDispenser<Node.ApplyRouting> get() = _applyRoutingDisp
        private lateinit var _applyRoutingDisp: FWDispenser<Node.ApplyRouting>

        override val acceptConnectionDisp: FWDispenser<Node.AcceptConnection> get() = _acceptConnectioDisp
        private lateinit var _acceptConnectioDisp: FWDispenser<Node.AcceptConnection>

        override val routTblUpdtDisp: FWDispenser<Node.RoutTblUpdt> get() = _routTblUpdtDisp
        private lateinit var _routTblUpdtDisp: FWDispenser<Node.RoutTblUpdt>

        override val shareRoutVectDisp: FWDispenser<Node.ShareRoutVect> get() = _shareRoutVectDisp
        private lateinit var _shareRoutVectDisp: FWDispenser<Node.ShareRoutVect>


        context(NetSimScope) override suspend fun initDispensers() {

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // RxUpdate
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _rxUpdateDisp =
                poolAggr.getOrAdd(Node.RxUpdt as FWId<Node.RxUpdt>) { pool, idx ->
                    object : Node.RxUpdt, MsgImpl<Node<*>, Node.RxUpdt>(pool, idx) {
                        override lateinit var netF: INetFlow
                        override var deltaRate: DataRate = DataRate.zero
                        override var toIntermediate: Boolean = false

                        context(Node<*>)
                        override suspend fun handle() {
                            assert(deltaRate.approx(DataRate.zero).not())
                            // TODO: apply dynamic policy

                            flowTable.rxUpdt(this)
                            handled()
                        }
                    }
                }



            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Connect
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _connectDisp =
                poolAggr.getOrAdd(Node.Connect as FWId<Node.Connect>) { pool, idx ->
                    object : Node.Connect, MsgImpl<Node<*>, Node.Connect>(pool, idx) {
                        override lateinit var other: Node<*>
                        override var updtRoutTbl: Boolean = true

                        context(NodeImpl<*>)
                        override suspend fun handle() {
                            // Used to refer to this anonymous object since labels (@label) cannot be used.
                            val thisMsg = this
                            val otherN = other as NodeImpl

                            assert(this@NodeImpl.isConnectedTo(otherN).not())

                            //
                            // Set up link from this node to `otherN`.
                            val lIdx = getFreeLinkIdx()
                            links[lIdx] = LinkImpl(
                                senderN = this@NodeImpl,
                                receiverN = otherN,
                                linkIdx = lIdx,
                            )

                            // Ask another node to accept connection.
                            acceptConnectionDisp.acquire().reset {
                                toBeAccepted = this@NodeImpl
                                updtRoutTbl = thisMsg.updtRoutTbl
                            }.sendTo(otherN, dispose = false).awaitHandling().dispose()

                            // Mark the routing table as to be shared.
                            routTbl.shared = false

                            // Update routing table.
                            if (updtRoutTbl) {
                                // Send this node's routing vector to the newly connected node.
                                routTblUpdtDisp.acquire().reset {
                                    from = this@NodeImpl
                                    routVect = routTbl.routVect
                                    updtRoutTbl = true
                                }.sendTo(otherN)

                                // The other node will be sending its routing vector to this.
                                // Until both nodes processed the new routing information and made
                                // the necessary adjustments, the network state is considered unstable.
                            }

                            handled()
                        }
                    }
            }



            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // AcceptConnection
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _acceptConnectioDisp = poolAggr.getOrAdd(Node.AcceptConnection as FWId<Node.AcceptConnection>) { pool, idx ->
                object : Node.AcceptConnection, MsgImpl<Node<*>, Node.AcceptConnection>(pool, idx) {
                    override lateinit var toBeAccepted: Node<*>
                    override var updtRoutTbl: Boolean = true

                    context(NodeImpl<*>)
                    override suspend fun handle() {
                        // TODO: needed?
//                        // Process all flow updates at the port level received so far.
//                        this@Node.portProcessAwait()

                        //
                        // Set up link from this node to `toBeAccepted`.
                        val lIdx = getFreeLinkIdx()
                        links[lIdx] = LinkImpl(
                            receiverN = toBeAccepted,
                            senderN = this@NodeImpl,
                            linkIdx = lIdx,
                        )

                        // Mark the routing table as to be shared.
                        routTbl.shared = false

                        if (updtRoutTbl) {
                            // Send this node's routing vector to the newly connected node.
                            routTblUpdtDisp.acquire().reset {
                                from = this@NodeImpl
                                routVect = routTbl.routVect
                                updtRoutTbl = true
                            }.sendTo(toBeAccepted)

                            // The other node will be sending its routing vector to this.
                            // Until both nodes processed the new routing information and made
                            // the necessary adjustments, the network state is considered unstable.
                        }

                        handled()
                    }
                }
            }



            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Disconnect
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _disconnectDisp =
                poolAggr.getOrAdd(Node.Disconnect as FWId<Node.Disconnect>) { pool, idx ->
                    object : Node.Disconnect, MsgImpl<Node<*>, Node.Disconnect>(pool, idx) {
                        override lateinit var other: Node<*>
                        override var notifyOther = false

                        context(NodeImpl<*>)
                        override suspend fun handle() {
                            TODO()
                        }
                    }
                }



            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // ApplyRouting
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _applyRoutingDisp =
                poolAggr.getOrAdd(Node.ApplyRouting as FWId<Node.ApplyRouting>) { pool, idx ->
                    object : Node.ApplyRouting, MsgImpl<Node<*>, Node.ApplyRouting>(pool, idx) {
                        context(NodeImpl<*>)
                        override suspend fun handle() {

                            flowTable.reapplyRouting()

//                            // Make ports reapply fairness policy.
//                            portProcessAwait()
                            TODO()

                            handled()
                        }
                    }
                }



            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // TblUpdt
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _routTblUpdtDisp =
                poolAggr.getOrAdd(Node.RoutTblUpdt as FWId<Node.RoutTblUpdt>) { pool, idx ->
                    object : Node.RoutTblUpdt, MsgImpl<Node<*>, Node.RoutTblUpdt>(pool, idx) {
                        override lateinit var from: Node<*>
                        override lateinit var routVect: RoutTbl2.RoutVect


                        context(NodeImpl<*>)
                        override suspend fun handle() {
                            //
                            // Update the routing table with new routing vector.
                            routTbl.updtWithInfoFrom(routVect).let { tblChanged ->
                                if (tblChanged) {
                                    // Mark the table as to be shared.
                                    routTbl.shared = false

                                    // Enqueue a `ShareRoutVect` msg.
                                    shareRoutVectDisp.acquire().reset().sendTo(this@NodeImpl)
                                }
                            }

                            handled()
                        }
                    }
                }



            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // ShareRoutVect
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _shareRoutVectDisp =
                poolAggr.getOrAdd(Node.ShareRoutVect as FWId<Node.ShareRoutVect>) { pool, idx ->
                    object : Node.ShareRoutVect, MsgImpl<Node<*>, Node.ShareRoutVect>(pool, idx) {

                        context(NodeImpl<*>)
                        override suspend fun handle() {
                            // A previous `ShareRoutVect` has already shared the current version of the routing table.
                            if (routTbl.shared) return handled()

                            //
                            // Share the new version of the routing table to all adjacent nodes.
                            coroutineScope {
                                links.asFlow().onEach { l ->
                                    if (l == null) return@onEach
                                    // Adjacent node to share the routing vector with.
                                    val adjN = l.receiverN
                                    routTblUpdtDisp.acquire().reset {
                                        from = this@NodeImpl
                                        routVect = this@NodeImpl.routTbl.routVect
                                    }.sendTo(adjN)
                                }.launchIn(this@coroutineScope)
                            }

                            // Mark the current version of the routing table as been shared.
                            routTbl.shared = true

                            handled()
                        }
                    }
                }
        }
    }
}
