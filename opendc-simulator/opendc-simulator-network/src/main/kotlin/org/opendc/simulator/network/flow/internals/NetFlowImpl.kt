package org.opendc.simulator.network.flow.internals

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.`eventEmitter-old`.publics.Event
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorFlow
import org.opendc.simulator.network.utils.invalidatable.internals.MutableInvalidatorFlow
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.MsgImpl

internal class NetFlowImpl private constructor(
    override val senderId: NodeId,
    override val destId: NodeId,
    override val id: FlowId,
    demand: DataRate,
    override val stabilizer: NetSimStabilizer,
): INetFlow {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // INetFlow
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override lateinit var senderNode: SenderNode<*>
    override var throughput: DataRate = DataRate.zero
        private set
    override var demand: DataRate = demand
        private set

    override suspend fun setDemand(demand: DataRate) {
        val msg = setDemandDisp.acquire().reset()
        msg.newDemand = demand
        msgChl.send(msg)
    }

    override suspend fun setThroughput(newThroughput: DataRate) {
        val notif = setTputDisp.acquire().reset()
        notif.newThroughput = newThroughput
        msgChl.send(notif)
    }

    override suspend fun msgAsyncIncreaseTputBy(amount: DataRate) {
        val notif = increaseTputDisp.acquire().reset()
        notif.amount = amount
        msgChl.send(notif)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope) override fun netLaunch(): Job = scopeLaunch {
        while (isActive) {
            _notificationChl.receive().handle()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // EventEmitter
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _eventFlow: MutableInvalidatorFlow<Event<NetFlow>> = MutableInvalidatorFlow()
    override val eventFlow: InvalidatorFlow<Event<NetFlow>> = _eventFlow

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _notificationChl: Channel<Msg<INetFlow, *>> = InvalidatorChl(this)
    override val msgChl: SendChannel<Msg<INetFlow, *>> = _notificationChl

    private val _priorityNotificationChl: Channel<Msg<INetFlow, *>> = InvalidatorChl(this)
    override val priorityMsgChl: SendChannel<Msg<INetFlow, *>> = _priorityNotificationChl

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun toString(): String = "NetFlow(id=$id)"

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFlowVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Serializable
    @SerialName("V1")
    internal companion object: NetFlowVersion {
        context(NetSimScope)
        override suspend operator fun invoke(
            senderId: NodeId,
            destId: NodeId,
            id: FlowId?,
            demand: DataRate,
        ): NetFlowImpl = NetFlowImpl(
            senderId = senderId,
            destId = destId,
            id = id ?: idDispenser.getFlowId(),
            demand = demand,
            stabilizer = barrier.stabilizer(),
        )

        override val setDemandDisp: FWDispenser<INetFlow.SetDemand> get() = _setDemandDisp
        private lateinit var _setDemandDisp: FWDispenser<INetFlow.SetDemand>

        override val demandChangedDisp: FWDispenser<NetFlow.DemandChanged> get() = _demandChangedDisp
        private lateinit var _demandChangedDisp: FWDispenser<NetFlow.DemandChanged>

        override val throughputChangedDisp: FWDispenser<NetFlow.ThroughputChanged> get() = _throughputChangedDisp
        private lateinit var _throughputChangedDisp: FWDispenser<NetFlow.ThroughputChanged>

        override val fragmentCompletedDisp: FWDispenser<NetFlow.FragmentCompleted> get() = _fragmentCompletedDisp
        private lateinit var _fragmentCompletedDisp: FWDispenser<NetFlow.FragmentCompleted>

        override val setTputDisp: FWDispenser<INetFlow.SetThroughput> get() = _setTputDisp
        private lateinit var _setTputDisp: FWDispenser<INetFlow.SetThroughput>

        override val increaseTputDisp: FWDispenser<INetFlow.IncreaseThroughput> get() = _increaseTputDisp
        private lateinit var _increaseTputDisp: FWDispenser<INetFlow.IncreaseThroughput>

        context(NetSimScope)
        override suspend fun initDispensers() {
            _setDemandDisp = poolAggr.getOrAdd(INetFlow.SetDemand as FWId<INetFlow.SetDemand>) { pool, idx ->
                val stab = barrier.stabilizer()
                object : INetFlow.SetDemand, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetDemand>() {
                    override val pool: FWPool<INetFlow.SetDemand, FWId<INetFlow.SetDemand>> = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab
                    override var newDemand: DataRate = DataRate.zero

                    context(NetFlow)
                    override suspend fun handle() {
                        val f = this@NetFlow as NetFlowImpl
                        val old: DataRate = f.demand
                        f.demand = newDemand
                        val deltaDemand = newDemand - old
                        //TODO
//                        val evnt = _demandChangedDisp.acquire()
//                        evnt.netFlow = f
//                        evnt.old = old
//                        evnt.new = f.demand
//                        f._eventFlow.emit(evnt)
                        f.senderNode.msgAsyncRxUpdt(deltaDemand, f)

                        dispose()
                    }
                }
            }.dispenser()



            _demandChangedDisp = poolAggr.getOrAdd(NetFlow.DemandChanged as FWId<NetFlow.DemandChanged>) { pool, idx ->
                object : NetFlow.DemandChanged, IFW<NetFlow.DemandChanged> {
                    override val pool: FWPool<NetFlow.DemandChanged, FWId<NetFlow.DemandChanged>> = pool
                    override val poolIdx: Idx = idx
                    override var old: DataRate = DataRate.zero
                    override var new: DataRate = DataRate.zero
                    override lateinit var netFlow: NetFlow
                }
            }.dispenser()



            _throughputChangedDisp = poolAggr.getOrAdd(NetFlow.ThroughputChanged as FWId<NetFlow.ThroughputChanged>) { pool, idx ->
                object : NetFlow.ThroughputChanged, IFW<NetFlow.ThroughputChanged> {
                    override val pool: FWPool<NetFlow.ThroughputChanged, FWId<NetFlow.ThroughputChanged>> = pool
                    override val poolIdx: Idx = idx
                    override var old: DataRate = DataRate.zero
                    override var new: DataRate = DataRate.zero
                    override lateinit var netFlow: NetFlow
                }
            }.dispenser()



            _fragmentCompletedDisp = poolAggr.getOrAdd(NetFlow.FragmentCompleted as FWId<NetFlow.FragmentCompleted>) { pool, idx ->
                object : NetFlow.FragmentCompleted, IFW<NetFlow.FragmentCompleted> {
                    override val pool: FWPool<NetFlow.FragmentCompleted, FWId<NetFlow.FragmentCompleted>> = pool
                    override val poolIdx: Idx = idx
                    override lateinit var netFlow: NetFlow
                }
            }.dispenser()



            _setTputDisp = poolAggr.getOrAdd(INetFlow.SetThroughput as FWId<INetFlow.SetThroughput>) { pool, idx ->
                    val stab = barrier.stabilizer()
                    object : INetFlow.SetThroughput, IInvalidatable, MsgImpl<INetFlow, INetFlow.SetThroughput>() {
                        override val stabilizer: NetSimStabilizer = stab
                        override val pool: FWPool<INetFlow.SetThroughput, FWId<INetFlow.SetThroughput>> = pool
                        override val poolIdx: Idx = idx
                        override var newThroughput: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl
                            val old: DataRate = throughput
                            f.throughput = newThroughput
                            val evnt = _throughputChangedDisp.acquire()
                            evnt.old = old
                            evnt.new = throughput
                            evnt.netFlow = f
                            f._eventFlow.emit(evnt)
                            dispose()
                        }
                    }
                }.dispenser()



            _increaseTputDisp = poolAggr.getOrAdd(INetFlow.IncreaseThroughput as FWId<INetFlow.IncreaseThroughput>) { pool, idx ->
                    val stab = barrier.stabilizer()
                    object : INetFlow.IncreaseThroughput, IInvalidatable, MsgImpl<INetFlow, INetFlow.IncreaseThroughput>() {
                        override val pool: FWPool<INetFlow.IncreaseThroughput, FWId<INetFlow.IncreaseThroughput>> = pool
                        override val poolIdx: Idx = idx
                        override val stabilizer: NetSimStabilizer = stab
                        override var amount: DataRate = DataRate.zero

                        context(NetFlow)
                        override suspend fun handle() {
                            val f = this@NetFlow as NetFlowImpl
                            val old: DataRate = throughput
                            f.throughput += amount
                            val evnt = _throughputChangedDisp.acquire()
                            evnt.netFlow = f
                            evnt.old = old
                            evnt.new = throughput
                            f._eventFlow.emit(evnt)
                            dispose()
                        }
                    }
                }.dispenser()
        }
    }
}
