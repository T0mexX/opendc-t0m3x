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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.invalidatable.InvalidatorChl
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.link.LinkImpl
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.MsgImpl
import org.opendc.simulator.network.components.networks.TopNodeMeta
import org.opendc.simulator.network.components.node.internalstructs.routtbl.RoutTblImpl
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.fwpool.FWDispenser
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.utils.SetOnce
import kotlin.coroutines.CoroutineContext

internal abstract class NodeImpl<Self : Node<Self>> protected constructor(
    final override val ip: IPv4Address,
    nPorts: Int,
) : Node<Self> {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Node
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override var topNodeMeta: TopNodeMeta<*>? = null

    override val links: MutableList<Link?> =
        ArrayList<Link?>(nPorts).also { l ->
            repeat(nPorts) { l.add(null) }
        }

    @Suppress("LeakingThis")
    override val routTbl: RoutTblImpl = RoutTblImpl(owner = this)

    override fun getFreeLinkIdx(): Int = links.indexOfFirst { it == null }.takeIf { it != -1 } ?: error("port not available")

    override suspend fun msgAsyncRxUpdt(
        deltaRate: DataRate,
        f: INetFlow,
    ) {
        assert(deltaRate.approx(DataRate.zero).not()) { deltaRate }

        rxUpdateDisp.acquire().reset {
            this.deltaRate = deltaRate
            this.f = f
        }.sendTo(this)
    }

    override suspend fun msgSyncConnect(
        other: Node<*>,
        linkBw: DataRate,
        updtRoutTbl: Boolean,
    ) {
        connectDisp.acquire().reset {
            this.other = other
            this.updtRoutTbl = updtRoutTbl
        }.sendTo(this, dispose = false).awaitHandling().dispose()
    }

    override suspend fun msgSyncDisconnect(other: Node<*>) {
        disconnectDisp.acquire().reset {
            this.other = other
        }.sendTo(this)
    }

    override suspend fun msgAsyncShareRoutVect() {
        shareRoutVectDisp.acquire().reset().sendTo(this)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetRunnable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @ProtectedUse
    override var job: Job by SetOnce()

//    context(NetSimScope)
//    @ProtectedUse
//    override fun netRun(additionalCtx: CoroutineContext) {
//        job =
//            launchInRoot(additionalCtx) {
//                while (isActive) {
//                    while (true) {
//                        // Accumulate multiple updates if possible
//                        // before telling the ports to process them and propagate results.
//                        _msgChl.tryReceiveValidate().getOrNull()?.handle()
//                            ?: break
//                    }
//
//                    //
//                    // Propagate updates to adjacent nodes.
//                    coroutineScope {
//                        flowTbl.updtTputs()
//                        config.routPolicy.onNodeTxAttempt()
//                        links.forEach { l ->
//                            launch { l?.attemptTx() }
//                        }
//                    }
//                    // Suspending receive. When node suspends here, its stability is validated.
//                    _msgChl.receive().handle()
//                    assert(stabilizer.isValidated.not())
//                }
//            }
//    }
    /**
     * The [Msg] that is currently being handled.
     * This property is used to mark the message as undelivered if coroutine is cancelled while handling it.
     *
     * Alternative would be to use `NetSimScope.wthContext(NonCancellable)`
     * to avoid cancellation during handling, but introducing overhead.
     */
    private var currMsg: Msg<Node<*>, *>? = null

    context(NetSimScope) @OptIn(ProtectedUse::class)
    override suspend fun netRunnableMain() {
        while (isActive) {
            while (true) {
                // Accumulate multiple updates if possible
                // before telling the ports to process them and propagate results.
                currMsg = _msgChl.tryReceiveValidate().getOrNull()
                currMsg?.handle() ?: break
            }

            //
            // Propagate updates to adjacent nodes.
            coroutineScope {
                flowTbl.updtTputs()
                config.routPolicy.onNodeTxAttempt()
                links.forEach { l ->
                    launch { l?.attemptTx() }
                }
            }

            //
            // Suspending receive. When node suspends here, its stability is validated.
            currMsg = null
            currMsg = _msgChl.receive()
            currMsg!!.handle()
            assert(stabilizer.isValidated.not())
        }
    }

    context(NetSimScope) @OptIn(ProtectedUse::class)
    override suspend fun netRunnableCancellationCleanup() {
        // If a message was received but not yet handled (coroutine canceled while handling it)
        // then mark it as undelivered.
        currMsg?.markUndelivered()
        // Drain all [Msg]s currently in the [msgChl] marking them as [Msg.State.UNDELIVERED]
        drainMsgChl()
        // Validate this [NetFlow] the last time to avoid deadlocks.
        this.validate()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Msgable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Suppress("LeakingThis")
    private val _msgChl: InvalidatorChl<Msg<Node<*>, *>> = InvalidatorChl(receiver = this)
    override val msgChl: SendChannel<Msg<Node<*>, *>> get() = _msgChl

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NodeVersion
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V0")
    companion object : NodeVersion {
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Msgs
        // //// Dispenser initialization for flyweight `Msg` objects related to `Node`s
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        override val rxUpdateDisp: FWDispenser<Node.RxUpdt> get() = _rxUpdateDisp
        private lateinit var _rxUpdateDisp: FWDispenser<Node.RxUpdt>

        override val connectDisp: FWDispenser<Node.Connect> get() = _connectDisp
        private lateinit var _connectDisp: FWDispenser<Node.Connect>

        override val disconnectDisp: FWDispenser<Node.Disconnect> get() = _disconnectDisp
        private lateinit var _disconnectDisp: FWDispenser<Node.Disconnect>

        override val applyRoutingDisp: FWDispenser<Node.ApplyRouting> get() = _applyRoutingDisp
        private lateinit var _applyRoutingDisp: FWDispenser<Node.ApplyRouting>

        override val acceptConnectionDisp: FWDispenser<Node.AcceptConnection> get() = _acceptConnectionDisp
        private lateinit var _acceptConnectionDisp: FWDispenser<Node.AcceptConnection>

        override val routTblUpdtDisp: FWDispenser<Node.RoutTblUpdt> get() = _routTblUpdtDisp
        private lateinit var _routTblUpdtDisp: FWDispenser<Node.RoutTblUpdt>

        override val shareRoutVectDisp: FWDispenser<Node.ShareRoutVect> get() = _shareRoutVectDisp
        private lateinit var _shareRoutVectDisp: FWDispenser<Node.ShareRoutVect>

        override val startFlowDisp: FWDispenser<Node.StartFlow> get() = _startFlowDisp
        private lateinit var _startFlowDisp: FWDispenser<Node.StartFlow>

        override val stopFlowDisp: FWDispenser<Node.StopFlow> get() = _stopFlowDisp
        private lateinit var _stopFlowDisp: FWDispenser<Node.StopFlow>

        context(NetSimScope)
        override suspend fun initDispensers() {
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // StartFlow
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _startFlowDisp =
                poolAggr.getOrAdd(Node.StartFlow as FWId<Node.StartFlow>) { pool, idx ->
                    object : Node.StartFlow, MsgImpl<Node<*>, Node.StartFlow>(pool, idx) {
                        override lateinit var f: INetFlow
                        override var ogDmnd: DataRate = DataRate.zero

                        context(Node<*>)
                        override suspend fun handle() {
                            require(this@Node is SenderNode<*>)
                            assert(f.srcId == this@Node.id)
                            val self = this

                            // The new flow is initialized through an [RxUpdate] with the original demand.
                            rxUpdateDisp.acquire().reset {
                                this.f = self.f
                                this.deltaRate = ogDmnd
                            }.handle()

                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // StopFlow
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _stopFlowDisp =
                poolAggr.getOrAdd(Node.StopFlow as FWId<Node.StopFlow>) { pool, idx ->
                    object : Node.StopFlow, MsgImpl<Node<*>, Node.StopFlow>(pool, idx) {
                        override lateinit var f: INetFlow

                        context(Node<*>)
                        override suspend fun handle() {
                            require(this@Node is SenderNode<*>)
                            assert(f.srcId == this@Node.id)

                            f.netCancel()
                            this@Node.flowTbl.reset(f)

                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // RxUpdate
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _rxUpdateDisp =
                poolAggr.getOrAdd(Node.RxUpdt as FWId<Node.RxUpdt>) { pool, idx ->
                    object : Node.RxUpdt, MsgImpl<Node<*>, Node.RxUpdt>(pool, idx) {
                        override lateinit var f: INetFlow
                        override var deltaRate: DataRate = DataRate.zero
                        override var toIntermediate: Boolean = false

                        context(Node<*>)
                        override suspend fun handle() {
                            assert(deltaRate.approx(DataRate.zero).not() || f.srcId == this@Node.id)
                            // TODO: apply dynamic policy

                            flowTbl.rxUpdt(this)
                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Connect
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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
                            links[lIdx] =
                                LinkImpl(
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

                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // AcceptConnection
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _acceptConnectionDisp =
                poolAggr.getOrAdd(Node.AcceptConnection as FWId<Node.AcceptConnection>) { pool, idx ->
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
                            links[lIdx] =
                                LinkImpl(
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

                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Disconnect
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // ApplyRouting
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _applyRoutingDisp =
                poolAggr.getOrAdd(Node.ApplyRouting as FWId<Node.ApplyRouting>) { pool, idx ->
                    object : Node.ApplyRouting, MsgImpl<Node<*>, Node.ApplyRouting>(pool, idx) {
                        context(NodeImpl<*>)
                        override suspend fun handle() {
                            flowTbl.reapplyRouting()

                            // Make ports reapply fairness policy.
                            // TODO: i dont remember

                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // TblUpdt
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _routTblUpdtDisp =
                poolAggr.getOrAdd(Node.RoutTblUpdt as FWId<Node.RoutTblUpdt>) { pool, idx ->
                    object : Node.RoutTblUpdt, MsgImpl<Node<*>, Node.RoutTblUpdt>(pool, idx) {
                        override lateinit var from: Node<*>
                        override lateinit var routVect: RoutTblImpl.RoutVect

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

                            markHandled()
                        }
                    }
                }

            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // ShareRoutVect
            // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            _shareRoutVectDisp =
                poolAggr.getOrAdd(Node.ShareRoutVect as FWId<Node.ShareRoutVect>) { pool, idx ->
                    object : Node.ShareRoutVect, MsgImpl<Node<*>, Node.ShareRoutVect>(pool, idx) {
                        context(NodeImpl<*>)
                        override suspend fun handle() {
                            // A previous `ShareRoutVect` has already shared the current version of the routing table.
                            if (routTbl.shared) return markHandled()

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

                            markHandled()
                        }
                    }
                }
        }
    }
}
