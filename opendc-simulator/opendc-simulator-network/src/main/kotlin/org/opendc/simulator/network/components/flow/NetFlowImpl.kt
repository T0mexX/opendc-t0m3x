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

@file:OptIn(InternalODCNApi::class)

package org.opendc.simulator.network.components.flow

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.annotations.DebuggingUse
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.integration.JNetFlow
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.evntemitter.EvntEmitter
import org.opendc.simulator.network.components.evntemitter.IEvntEmitter
import org.opendc.simulator.network.components.invalidatable.IInvalidatable
import org.opendc.simulator.network.components.invalidatable.InvalidatorChl
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.MsgImpl
import org.opendc.simulator.network.components.msgable.ReqMsgImpl
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimTmSrc
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.simscope.fwpool.FWDispenser
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.simscope.fwpool.FWPool
import org.opendc.simulator.network.simscope.fwpool.IFW
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.InternalODCNApi
import org.opendc.simulator.network.utils.NetCoId
import org.opendc.simulator.network.utils.SetOnce

internal class NetFlowImpl private constructor(
    override val srcId: NodeId,
    override val destId: NodeId,
    override val id: FlowId,
    dmnd: DataRate,
    override val stabilizer: NetSimStabilizer,
    tmSrc: NetSimTmSrc<*>,
) : INetFlow, EvntEmitter<NetFlow> by IEvntEmitter() {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // INetFlow
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override lateinit var senderNode: SenderNode<*>
    override var jNetFlow: JNetFlow? = null

    override var throughput: DataRate = DataRate.zero
        private set
    override var demand: DataRate = dmnd
        private set

    override suspend fun msgAsyncSetDemand(
        demand: DataRate,
        fragId: Any?,
    ) {
        assert(demand >= DataRate.zero)

        setDemandDisp.acquire().reset {
            this.newDemand = demand
            this.fragId = fragId
        }.sendTo(this)
    }

    override suspend fun msgAsyncSetTput(newTput: DataRate) {
        assert(newTput >= DataRate.zero)

        setTputDisp.acquire().reset {
            this.newTput = newTput.roundToIfWithinEpsilon(demand, epsilon = 1e-3)
        }.sendTo(this)
    }

    override suspend fun msgAsyncIncreaseTputBy(amount: DataRate) {
        assert(amount != DataRate.zero)

        increaseTputDisp.acquire().reset {
            this.amount = amount
        }.sendTo(this)
    }

    override suspend fun msgSyncReqFragComplEstimate(): Timestamp =
        reqTmRmDisp.acquire().reset().sendTo(this).awaitResponse()

    override suspend fun msgAsyncFragInit(
        target: DataSize,
        fragId: Any?,
    ) {
        assert(target >= DataSize.zero)

        fragInitDisp.acquire().reset {
            this.fragTarget = target
            this.fragId = fragId
        }.sendTo(this)
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetFlowImpl) return false

        return id == other.id
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Synchronizable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override var lastSync: Timestamp = tmSrc.tmstamp

    context(NetSimScope)
    override suspend fun sync(forceUpdt: Boolean): INetFlow {
        if (isSync() && forceUpdt.not()) return this

        // Time passed since last synchronization.
        val sinceSync = tmSrc.tmstamp timeDelta lastSync
        check(sinceSync >= TimeDelta.zero)

        fragCurr += throughput * sinceSync

        if (complEvntEmitted.not() && fragCurr approxLargerOrEq fragTarget) {
            // If the current fragment is completed, then emit the corresponding event.
            fragCurr = fragTarget
//            assert(fragComplEstimate approx tmSrc.tmstamp) { "estimate: $fragComplEstimate, tmstamp: ${tmSrc.tmstamp}" }
            evntFragCompleted()
        }

        lastSync = tmSrc.tmstamp

        return this
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fragment Logic
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var fragTarget: DataSize = DataSize.zero
    private var fragCurr: DataSize = DataSize.zero
    private var complEvntEmitted: Boolean = false
    private var fragId: Any? = null
    private val fragRemaining get() = fragTarget - fragCurr
    private var fragComplEstimate: Timestamp = Timestamp.max
    context(NetSimScope)
    private fun computeFragComplEstimate(): Timestamp {
        if (fragRemaining == DataSize.zero) return tmSrc.tmstamp
        val tmRm = fragRemaining / throughput
        return if (tmRm.value.isNaN()) Timestamp.max
        else tmSrc.tmstamp + tmRm
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetRunnable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @ProtectedUse
    override var job: Job by SetOnce()
//
//    context(NetSimScope)
//    @ProtectedUse
//    override fun netRun(additionalCtx: CoroutineContext) {
//        job =
//            launchInRoot(additionalCtx) {
//                try {
//                    while (isActive) {
//                        val msg = _msgChl.receive()
//                        try {
//                            msg.handle()
//                        } catch (ex: CancellationException) {
//                            msg.markHandled()
//                        }
//                    }
//                } finally {
//                    drainMsgChl()
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
    private var currMsg: Msg<INetFlow, *>? = null

    context(NetSimScope) @OptIn(ProtectedUse::class)
    override suspend fun netRunnableMain() {
        while (isActive) {
            //
            // Receive and handle one [Msg] at the time.
            currMsg = _msgChl.receive()
            currMsg!!.handle()
            currMsg = null
        }
    }

    context(NetSimScope) @OptIn(ProtectedUse::class, DebuggingUse::class)
    override suspend fun netRunnableCancellationCleanup() {
        // If a message was received but not yet handled (coroutine canceled while handling it)
        // then mark it as undelivered.
        currMsg?.markUndelivered()
        // Drain all [Msg]s currently in the [msgChl] marking them as [Msg.State.UNDELIVERED]
        drainMsgChl()
        // Validate this [NetFlow] the last time to avoid deadlocks.
        this.validate()
        log.debug("{} was cancelled", this@NetFlowImpl) // TODO: rmln
//        log.debug("{}", barrier.getInvalidated()) // TODO: rmln
    }
//
//    context(NetSimScope) @OptIn(DelicateCoroutinesApi::class, DebuggingUse::class)
//    private suspend fun drainMsgChl() {
//        // Close the msg channel so that no more [Msg]s can be received.
//        _msgChl.close()
//        var nDrained = 0
//
//        // Handled the [Msg]s that are still in [msgChl].
//        while (_msgChl.isClosedForReceive.not()) {
//            _msgChl.tryReceiveValidate().getOrThrow().markUndelivered()
//            nDrained++
//        }
//        log.debug("{} was cancelled with {} drained msgs", this, nDrained)
//        log.debug("{}", barrier.getInvalidated()) // TODO: rmln
//
//        // Validate this [NetFlow] the last time to avoid deadlocks.
//        this.validate()
//    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // EventEmitter
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun evntTputChanged(
        old: DataRate,
        new: DataRate,
    ) {
        if (nListeners > 0) {
            tputChangedDisp.acquire().reset {
                this.f = this@NetFlowImpl
                this.old = old
                this.new = new
                this.newComplEstimate = computeFragComplEstimate()
                this.fragId = this@NetFlowImpl.fragId
            }.emit(from = this@NetFlowImpl)
        }
    }

    private suspend fun evntFragCompleted() {
        if (nListeners > 0) {
            fragComplDisp.acquire().reset {
                this.f = this@NetFlowImpl
                this.fragId = this@NetFlowImpl.fragId
            }.emit(from = this@NetFlowImpl)
        }
        complEvntEmitted = true
    }

    private suspend fun evntFragComplEstimateChanged(
        new: Timestamp,
        old: Timestamp,
    ) {
        if (nListeners > 0) {
            tmRmChangedDisp.acquire().reset {
                this.f = this@NetFlowImpl
                this.new = new
                this.old = old
                this.fragId = this@NetFlowImpl.fragId
            }.emit(from = this@NetFlowImpl)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Msgable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _msgChl: InvalidatorChl<Msg<INetFlow, *>> = InvalidatorChl(this)
    override val msgChl: SendChannel<Msg<INetFlow, *>> = _msgChl

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Other
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun toString(): String = "NetFlow(id=$id,src=${srcId.toIp()},dest=${destId.toIp()},dmnd=$demand,tput=$throughput)"

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFlowVersion
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V1")
    internal companion object : NetFlowVersion {
        context(NetSimScope)
        @OptIn(ProtectedUse::class, DebuggingUse::class)
        override suspend operator fun invoke(
            srcId: NodeId,
            destId: NodeId,
            id: FlowId?,
            dmnd: DataRate,
        ): NetFlowImpl {
            assert(destId != srcId)
            return NetFlowImpl(
                srcId = srcId,
                destId = destId,
                id = id ?: idDispenser.getFlowId(),
                dmnd = dmnd,
                stabilizer = barrier.stabilizer(NetFlow::class),
                tmSrc = tmSrc,
            ).also { f ->
                f.invalidate()

                //
                // Start the coroutine that runs the flow.
                val coId = NetCoId.new(NetCo.FLOW)
                val coName = CoroutineName("NetFlow(id:${coId.value})")
                f.netRun(coId + coName)

                // For debugging.
                f.stabilizer.owner = f
//                f.job.invokeOnCompletion { cause ->
//                    cause?.let {
//                        throw it
//                    }
//                }
            }
        }

        override val setDemandDisp: FWDispenser<INetFlow.SetDemand> get() = _setDemandDisp
        private lateinit var _setDemandDisp: FWDispenser<INetFlow.SetDemand>

//        override val demandChangedDisp: FWDispenser<NetFlow.DemandChanged> get() = _demandChangedDisp
//        private lateinit var _demandChangedDisp: FWDispenser<NetFlow.DemandChanged>
//
        override val tputChangedDisp: FWDispenser<NetFlow.TPutChanged> get() = _tputChangedDisp
        private lateinit var _tputChangedDisp: FWDispenser<NetFlow.TPutChanged>
//
//        override val fragmentCompletedDisp: FWDispenser<NetFlow.FragmentCompleted> get() = _fragmentCompletedDisp
//        private lateinit var _fragmentCompletedDisp: FWDispenser<NetFlow.FragmentCompleted>

        override val setTputDisp: FWDispenser<INetFlow.SetThroughput> get() = _setTputDisp
        private lateinit var _setTputDisp: FWDispenser<INetFlow.SetThroughput>

        override val increaseTputDisp: FWDispenser<INetFlow.IncreaseThroughput> get() = _increaseTputDisp
        private lateinit var _increaseTputDisp: FWDispenser<INetFlow.IncreaseThroughput>

        override val reqTmRmDisp: FWDispenser<INetFlow.ReqFragComplEstimate> get() = _reqTmRmDisp
        private lateinit var _reqTmRmDisp: FWDispenser<INetFlow.ReqFragComplEstimate>

        override val fragInitDisp: FWDispenser<INetFlow.FragInit> get() = _fragInitDisp
        private lateinit var _fragInitDisp: FWDispenser<INetFlow.FragInit>

        override val fragComplDisp: FWDispenser<NetFlow.FragCompl> get() = _fragComplDisp
        private lateinit var _fragComplDisp: FWDispenser<NetFlow.FragCompl>

        override val tmRmChangedDisp: FWDispenser<NetFlow.FragComplEstimateChanged> get() = _tmRmChangedDisp
        private lateinit var _tmRmChangedDisp: FWDispenser<NetFlow.FragComplEstimateChanged>

        context(NetSimScope)
        override suspend fun initDispensers() {
            _setDemandDisp =
                poolAggr.getOrAdd(INetFlow.SetDemand as FWId<INetFlow.SetDemand>) { pool, idx ->
                    val stab = barrier.stabilizer(INetFlow.SetDemand::class)
                    object : INetFlow.SetDemand, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetDemand>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab
                        override var newDemand: DataRate = DataRate.zero
                        override var fragId: Any? = null

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl

                            if (f.fragId !== fragId) return markHandled()
                            if (newDemand approx f.demand) return markHandled()

                            val old: DataRate = f.demand
                            f.demand = newDemand
                            val deltaDemand = newDemand - old

                            f.senderNode.msgAsyncRxUpdt(deltaDemand, f)

                            markHandled()
                        }

                        override fun toString(): String = "SetDemand"
                    }
                }

//            _demandChangedDisp = poolAggr.getOrAdd(NetFlow.DemandChanged as FWId<NetFlow.DemandChanged>) { pool, idx ->
//                object : NetFlow.DemandChanged, IFW<NetFlow.DemandChanged> {
//                    override val pool: FWPool<NetFlow.DemandChanged, FWId<NetFlow.DemandChanged>> = pool
//                    override val poolIdx: Idx = idx
//                    override var old: DataRate = DataRate.zero
//                    override var new: DataRate = DataRate.zero
//                    override lateinit var netFlow: NetFlow
//                }
//            }.dispenser()
//
//
//
            _setTputDisp =
                poolAggr.getOrAdd(INetFlow.SetThroughput as FWId<INetFlow.SetThroughput>) { pool, idx ->
                    val stab = barrier.stabilizer(INetFlow.SetThroughput::class)
                    object : INetFlow.SetThroughput, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetThroughput>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab
                        override var newTput: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl
                            if (newTput approx f.throughput) return markHandled()

                            val oldTput: DataRate = throughput
                            val oldFragComplEstimate = f.fragComplEstimate

                            f.throughput = newTput
                            f.fragComplEstimate = f.computeFragComplEstimate()

                            // If there are collectors listening to this `NetFlow` events,
                            // then emit events to those collectors.
                            f.evntTputChanged(old = oldTput, new = f.throughput)
                            if (f.fragComplEstimate > tmSrc.tmstamp)
                                f.evntFragComplEstimateChanged(old = oldFragComplEstimate, new = f.fragComplEstimate)

                            markHandled()
                        }

                        override fun toString(): String = "SetTput"
                    }
                }

            _increaseTputDisp =
                poolAggr.getOrAdd(INetFlow.IncreaseThroughput as FWId<INetFlow.IncreaseThroughput>) { pool, idx ->
                    val stab = barrier.stabilizer(INetFlow.IncreaseThroughput::class)
                    object : INetFlow.IncreaseThroughput,
                        IInvalidatable, MsgImpl<INetFlow, INetFlow.IncreaseThroughput>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab
                        override var amount: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl
                            if (amount == DataRate.zero) return markHandled()

                            val oldTput: DataRate = throughput
                            val oldFragComplEstimate = f.fragComplEstimate

                            f.throughput += amount
                            f.fragComplEstimate = f.computeFragComplEstimate()

                            // If there are collectors listening to this `NetFlow` events,
                            // then emit events to those collectors.
                            f.evntTputChanged(old = oldTput, new = f.throughput)
                            if (f.fragComplEstimate > tmSrc.tmstamp)
                                f.evntFragComplEstimateChanged(old = oldFragComplEstimate, new = f.fragComplEstimate)

                            markHandled()
                        }
                    }
                }

            _reqTmRmDisp =
                poolAggr.getOrAdd(INetFlow.ReqFragComplEstimate as FWId<INetFlow.ReqFragComplEstimate>) { pool, idx ->
                    object : INetFlow.ReqFragComplEstimate, ReqMsgImpl<INetFlow, Timestamp, INetFlow.ReqFragComplEstimate>(pool, idx) {
                        context(INetFlow)
                        override suspend fun handle() {
                            val f = this@INetFlow as NetFlowImpl

                            respond(f.computeFragComplEstimate())
                        }

                        override fun toString(): String = "ReqFragComplEstimate"
                    }
                }

            _fragInitDisp =
                poolAggr.getOrAdd(INetFlow.FragInit as FWId<INetFlow.FragInit>) { pool, idx ->
                    object : INetFlow.FragInit, MsgImpl<INetFlow, INetFlow.FragInit>(pool, idx) {
                        override var fragTarget: DataSize = DataSize.zero
                        override var fragId: Any? = null

                        context(INetFlow)
                        override suspend fun handle() {
                            val f = this@INetFlow as NetFlowImpl

                            f.fragCurr = DataSize.zero
                            f.fragId = fragId
                            f.fragTarget = fragTarget
                            f.fragComplEstimate = f.computeFragComplEstimate()
                            if (fragTarget == DataSize.zero) {
                                f.evntFragCompleted()
                            } else {
                                f.complEvntEmitted = false
                            }

                            markHandled()
                        }

                        override fun toString(): String = "FragInit"
                    }
                }

            _fragComplDisp =
                poolAggr.getOrAdd(NetFlow.FragCompl as FWId<NetFlow.FragCompl>) { pool, idx ->
                    val stab = barrier.stabilizer(NetFlow.FragComplEstimateChanged::class)
                    object : NetFlow.FragCompl(), IFW<NetFlow.FragCompl>, IInvalidatable {
                        override val pool = pool
                        override val poolIdx: Idx = idx
                        override lateinit var f: NetFlow
                        override var fragId: Any? = null
                        override val stabilizer: NetSimStabilizer = stab
                    }
                }

            _tmRmChangedDisp =
                poolAggr.getOrAdd(NetFlow.FragComplEstimateChanged as FWId<NetFlow.FragComplEstimateChanged>) { pool, idx ->
                    val stab = barrier.stabilizer(NetFlow.FragComplEstimateChanged::class)
                    object : NetFlow.FragComplEstimateChanged(), IFW<NetFlow.FragComplEstimateChanged>, IInvalidatable {
                        override val pool = pool
                        override val poolIdx: Idx = idx
                        override lateinit var f: NetFlow
                        override var new: Timestamp = Timestamp.max
                        override var old: Timestamp = Timestamp.max
                        override var fragId: Any? = null
                        override val stabilizer: NetSimStabilizer = stab
                    }
                }

            _tputChangedDisp =
                poolAggr.getOrAdd(NetFlow.TPutChanged as FWId<NetFlow.TPutChanged>) { pool, idx ->
                    val stab = barrier.stabilizer(NetFlow.FragComplEstimateChanged::class)
                    object : NetFlow.TPutChanged(), IFW<NetFlow.TPutChanged>, IInvalidatable {
                        override val pool: FWPool<NetFlow.TPutChanged, FWId<NetFlow.TPutChanged>> = pool
                        override val poolIdx: Idx = idx
                        override var old: DataRate = DataRate.zero
                        override var new: DataRate = DataRate.zero
                        override var newComplEstimate: Timestamp = Timestamp.max
                        override var fragId: Any? = null
                        override lateinit var f: NetFlow
                        override val stabilizer: NetSimStabilizer = stab
                    }
                }
        }
    }
}
