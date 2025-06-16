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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.evntemitter.EvntEmitter
import org.opendc.simulator.network.components.evntemitter.IEvntEmitter
import org.opendc.simulator.network.components.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.components.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.MsgImpl
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.simscope.fwpool.FWDispenser
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.simscope.fwpool.FWPool
import org.opendc.simulator.network.simscope.fwpool.IFW
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.InternalODCNetworkApi

internal class NetFlowImpl private constructor(
    override val senderId: NodeId,
    override val destId: NodeId,
    override val id: FlowId,
    demand: DataRate,
    override val stabilizer: NetSimStabilizer,
) : INetFlow, EvntEmitter<NetFlow> by IEvntEmitter() {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // INetFlow
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override lateinit var senderNode: SenderNode<*>
    override var throughput: DataRate = DataRate.zero
        private set
    override var demand: DataRate = demand
        private set

    override suspend fun setDemand(demand: DataRate) {
        assert(demand >= DataRate.zero)

        val msg = setDemandDisp.acquire().reset()
        msg.newDemand = demand
        msg.sendTo(this)
    }

    override suspend fun msgAsyncSetTput(newTput: DataRate) {
        assert(newTput >= DataRate.zero)

        val msg = setTputDisp.acquire().reset()
        msg.newThroughput = newTput.roundToIfWithinEpsilon(demand, epsilon = 1e-3)
        msg.sendTo(this)
    }

    override suspend fun msgAsyncIncreaseTputBy(amount: DataRate) {
        val msg = increaseTputDisp.acquire().reset()
        msg.amount = amount
        msg.sendTo(this)
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetFlowImpl) return false

        return id == other.id
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope)
    override suspend fun netLaunch(scope: CoroutineScope): Job =
        scope.launch(CoroutineID.new()) {
            while (isActive) {
                _msgChl.receive().handle()
            }
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
        if (nCollectors > 0) {
            tputChangedDisp.acquire().reset {
                this.netFlow = this@NetFlowImpl
                this.old = old
                this.new = new
            }.emit(from = this@NetFlowImpl)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Msgable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _msgChl: Channel<Msg<INetFlow, *>> = InvalidatorChl(this)
    override val msgChl: SendChannel<Msg<INetFlow, *>> = _msgChl

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Other
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun toString(): String = "NetFlow(id=$id)"

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFlowVersion
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V1")
    internal companion object : NetFlowVersion {
        context(NetSimScope)
        override suspend operator fun invoke(
            senderId: NodeId,
            destId: NodeId,
            id: FlowId?,
            demand: DataRate,
        ): NetFlowImpl {
            assert(destId != senderId)
            return NetFlowImpl(
                senderId = senderId,
                destId = destId,
                id = id ?: idDispenser.getFlowId(),
                demand = demand,
                stabilizer = barrier.stabilizer(),
            ).also {
                it.invalidate()
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

        context(NetSimScope)
        override suspend fun initDispensers() {
            _setDemandDisp =
                poolAggr.getOrAdd(INetFlow.SetDemand as FWId<INetFlow.SetDemand>) { pool, idx ->
                    val stab = barrier.stabilizer()
                    object : INetFlow.SetDemand, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetDemand>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab
                        override var newDemand: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl

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
            _tputChangedDisp =
                poolAggr.getOrAdd(NetFlow.TPutChanged as FWId<NetFlow.TPutChanged>) { pool, idx ->
                    val stab = barrier.stabilizer()
                    object : NetFlow.TPutChanged(), IFW<NetFlow.TPutChanged> {
                        override val pool: FWPool<NetFlow.TPutChanged, FWId<NetFlow.TPutChanged>> = pool
                        override val poolIdx: Idx = idx
                        override val stabilizer: NetSimStabilizer = stab
                        override var old: DataRate = DataRate.zero
                        override var new: DataRate = DataRate.zero
                        override lateinit var netFlow: NetFlow
                    }
                }
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
                    val stab = barrier.stabilizer()
                    object : INetFlow.SetThroughput, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetThroughput>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab
                        override var newThroughput: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl
                            val old: DataRate = throughput
                            f.throughput = newThroughput

                            // If there are collectors listening to this `NetFlow` events,
                            // then emit events to those collectors.
                            this@NetFlow.evntTputChanged(old = old, new = f.throughput)

                            handled()
                        }
                    }
                }

            _increaseTputDisp =
                poolAggr.getOrAdd(INetFlow.IncreaseThroughput as FWId<INetFlow.IncreaseThroughput>) { pool, idx ->
                    val stab = barrier.stabilizer()
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
        }
    }
}
