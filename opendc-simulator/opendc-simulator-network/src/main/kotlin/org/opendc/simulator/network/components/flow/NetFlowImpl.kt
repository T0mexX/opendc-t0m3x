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

@file:OptIn(InternalODCNetworkApi::class)

package org.opendc.simulator.network.components.flow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import org.opendc.simulator.network.utils.InternalODCNetworkApi
import org.opendc.simulator.network.utils.NetCoId
import kotlin.coroutines.CoroutineContext

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
        increaseTputDisp.acquire().reset {
            this.amount = amount
        }.sendTo(this)
    }

    override suspend fun msgSyncReqTmRm(): TimeDelta = reqTmRmDisp.acquire().reset().sendTo(this).awaitResponse()

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

        val oldFragTmRm = (fragTarget - fragCurr).max(fragTarget) / throughput
        fragCurr += throughput * sinceSync
        val newFragTmRm = (fragTarget - fragCurr).max(fragTarget) / throughput

        if (complEvntEmitted.not() && fragCurr approxLargerOrEq fragTarget) {
            // If the current fragment is completed, then emit the corresponding event.
            fragCurr = fragTarget
            evntFragCompleted()
        } else if (newFragTmRm != oldFragTmRm) {
            // Else if the expected remaining time to complete the fragment changed, then emit the corresponding event.
            evntTmRmChanged(new = newFragTmRm, old = oldFragTmRm)
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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetRunnable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @ProtectedUse
    override lateinit var job: Job

    context(NetSimScope)
    @ProtectedUse
    override fun netRun(additionalCtx: CoroutineContext) {
        job =
            launchInRoot(additionalCtx) {
                try {
                    while (isActive) {
                        val msg = _msgChl.receive()
                        try {
                            msg.handle()
                        } catch (ex: CancellationException) {
                            msg.handled()
                        }
                    }
                } finally {
                    drainMsgChl()
                }
            }
    }

    context(NetSimScope)
    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun drainMsgChl() {
        // Close the msg channel so that no more [Msg]s can be received.
        _msgChl.close()

        // Handled the [Msg]s that are still in [msgChl].
        while (_msgChl.isClosedForReceive.not()) {
            _msgChl.receive().handle()
        }

        // Validate this [NetFlow] the last time to avoid deadlocks.
        this.validate()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // EventEmitter
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * TODO
     */
    private suspend fun evntTputChanged(
        old: DataRate,
        new: DataRate,
    ) {
        if (nListeners > 0) {
            tputChangedDisp.acquire().reset {
                this.f = this@NetFlowImpl
                this.old = old
                this.new = new
                this.newTmRm = (fragTarget - fragCurr) / throughput
            }.emit(from = this@NetFlowImpl)
        }
    }

    /**
     * TODO
     */
    private suspend fun evntFragCompleted() {
        if (nListeners > 0) {
            fragComplDisp.acquire().reset {
                this.f = this@NetFlowImpl
                this.fragId = this@NetFlowImpl.fragId
            }.emit(from = this@NetFlowImpl)
        }
        complEvntEmitted = true
    }

    private suspend fun evntTmRmChanged(
        new: TimeDelta,
        old: TimeDelta,
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

    override fun toString(): String = "NetFlow(id=$id,src=${srcId.toIp()},dest=${destId.toIp()},ogDmnd=$demand,tput=$throughput)"

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFlowVersion
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V1")
    internal companion object : NetFlowVersion {
        context(NetSimScope)
        @OptIn(ProtectedUse::class)
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
            ).also {
                it.invalidate()

                //
                // Start the coroutine that runs the flow.
                val coId = NetCoId.new(NetCo.FLOW)
                val coName = CoroutineName("NetFlow(id:${coId.value})")
                it.netRun(coId + coName)
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

        override val reqTmRmDisp: FWDispenser<INetFlow.ReqTmRm> get() = _reqTmRmDisp
        private lateinit var _reqTmRmDisp: FWDispenser<INetFlow.ReqTmRm>

        override val fragInitDisp: FWDispenser<INetFlow.FragInit> get() = _fragInitDisp
        private lateinit var _fragInitDisp: FWDispenser<INetFlow.FragInit>

        override val fragComplDisp: FWDispenser<NetFlow.FragCompl> get() = _fragComplDisp
        private lateinit var _fragComplDisp: FWDispenser<NetFlow.FragCompl>

        override val tmRmChangedDisp: FWDispenser<NetFlow.TmRmChanged> get() = _tmRmChangedDisp
        private lateinit var _tmRmChangedDisp: FWDispenser<NetFlow.TmRmChanged>

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
                            if (f.fragId !== fragId) return handled()

                            // If the requested demand is the same no changes made.
                            if (newDemand approx f.demand) return handled()

                            val old: DataRate = f.demand
                            f.demand = newDemand
                            val deltaDemand = newDemand - old
                            // TODO
//                        val evnt = _demandChangedDisp.acquire()
//                        evnt.netFlow = f
//                        evnt.old = old
//                        evnt.new = f.demand
//                        f._eventFlow.emit(evnt)
                            f.senderNode.msgAsyncRxUpdt(deltaDemand, f)

                            handled()
                        }
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

//
//
//
//            _fragmentCompletedDisp = poolAggr.getOrAdd(NetFlow.FragmentCompleted as FWId<NetFlow.FragmentCompleted>) { pool, idx ->
//                object : NetFlow.FragmentCompleted, IFW<NetFlow.FragmentCompleted> {
//                    override val pool: FWPool<NetFlow.FragmentCompleted, FWId<NetFlow.FragmentCompleted>> = pool
//                    override val poolIdx: Idx = idx
//                    override lateinit var netFlow: NetFlow
//                }
//            }.dispenser()

            _setTputDisp =
                poolAggr.getOrAdd(INetFlow.SetThroughput as FWId<INetFlow.SetThroughput>) { pool, idx ->
                    val stab = barrier.stabilizer(INetFlow.SetThroughput::class)
                    object : INetFlow.SetThroughput, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetThroughput>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab
                        override var newTput: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl
                            val old: DataRate = throughput
                            f.throughput = newTput

                            // If there are collectors listening to this `NetFlow` events,
                            // then emit events to those collectors.
                            this@NetFlow.evntTputChanged(old = old, new = f.throughput)

                            handled()
                        }
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
                            val old: DataRate = throughput
                            f.throughput += amount

                            // If there are collectors listening to this `NetFlow` events,
                            // then emit events to those collectors.
                            this@NetFlow.evntTputChanged(old = old, new = f.throughput)

                            handled()
                        }
                    }
                }

            _reqTmRmDisp =
                poolAggr.getOrAdd(INetFlow.ReqTmRm as FWId<INetFlow.ReqTmRm>) { pool, idx ->
                    object : INetFlow.ReqTmRm, ReqMsgImpl<INetFlow, TimeDelta, INetFlow.ReqTmRm>(pool, idx) {
                        context(INetFlow)
                        override suspend fun handle() {
                            val f = this@INetFlow as NetFlowImpl

                            respond(
                                ((f.fragTarget - f.fragCurr) / f.throughput) max TimeDelta.zero,
                            )
                        }
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
                            if (fragTarget != DataSize.zero) {
                                f.complEvntEmitted = false
                            } else {
                                f.complEvntEmitted = true
                            }

                            handled()
                        }
                    }
                }

            _fragComplDisp =
                poolAggr.getOrAdd(NetFlow.FragCompl as FWId<NetFlow.FragCompl>) { pool, idx ->
                    val stab = barrier.stabilizer(NetFlow.TmRmChanged::class)
                    object : NetFlow.FragCompl(), IFW<NetFlow.FragCompl>, IInvalidatable {
                        override val pool = pool
                        override val poolIdx: Idx = idx
                        override lateinit var f: NetFlow
                        override var fragId: Any? = null
                        override val stabilizer: NetSimStabilizer = stab
                    }
                }

            _tmRmChangedDisp =
                poolAggr.getOrAdd(NetFlow.TmRmChanged as FWId<NetFlow.TmRmChanged>) { pool, idx ->
                    val stab = barrier.stabilizer(NetFlow.TmRmChanged::class)
                    object : NetFlow.TmRmChanged(), IFW<NetFlow.TmRmChanged>, IInvalidatable {
                        override val pool = pool
                        override val poolIdx: Idx = idx
                        override lateinit var f: NetFlow
                        override var new: TimeDelta = TimeDelta.zero
                        override var old: TimeDelta = TimeDelta.zero
                        override var fragId: Any? = null
                        override val stabilizer: NetSimStabilizer = stab
                    }
                }

            _tputChangedDisp =
                poolAggr.getOrAdd(NetFlow.TPutChanged as FWId<NetFlow.TPutChanged>) { pool, idx ->
                    val stab = barrier.stabilizer(NetFlow.TmRmChanged::class)
                    object : NetFlow.TPutChanged(), IFW<NetFlow.TPutChanged>, IInvalidatable {
                        override val pool: FWPool<NetFlow.TPutChanged, FWId<NetFlow.TPutChanged>> = pool
                        override val poolIdx: Idx = idx
                        override var old: DataRate = DataRate.zero
                        override var new: DataRate = DataRate.zero
                        override var newTmRm: TimeDelta = TimeDelta.zero
                        override lateinit var f: NetFlow
                        override val stabilizer: NetSimStabilizer = stab
                    }
                }
        }
    }
}
