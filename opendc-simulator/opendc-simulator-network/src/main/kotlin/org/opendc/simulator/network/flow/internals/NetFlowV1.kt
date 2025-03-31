package org.opendc.simulator.network.flow.internals

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.`eventEmitter-old`.publics.Event
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorFlow
import org.opendc.simulator.network.utils.invalidatable.internals.MutableInvalidatorFlow
import org.opendc.simulator.network.utils.notifiable.publics.Notification

internal class NetFlowV1 private constructor(
    override val senderId: NodeId,
    override val destId: NodeId,
    override val id: FlowId,
    demand: DataRate,
    override val stabilizer: NetSimStabilizer,
): INetFlow, IInvalidatable, Launchable {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // INetFlow
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override var throughput: DataRate = DataRate.zero
        private set
    override var demand: DataRate = demand
        private set

    override suspend fun setDemand(demand: DataRate) {
        val notif = setDemandNotifDispenser.acquire()
        notif.newDemand = demand
        notificationChl.send(notif)
    }

    override suspend fun setThroughput(newThroughput: DataRate) {
        val notif = setTputNotifDispenser.acquire()
        notif.newThroughput = newThroughput
        notificationChl.send(notif)
    }

    override suspend fun increaseThroughputBy(amount: DataRate) {
        val notif = increaseTputNotifDispenser.acquire()
        notif.amount = amount
        notificationChl.send(notif)
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

    private lateinit var tputChangedEvntDispenser: FWDispenser<NetFlow.ThroughputChanged>
    private lateinit var fragCompletedEvntDispenser: FWDispenser<NetFlow.FragmentCompleted>
    private lateinit var demandChangedEvntDispenser: FWDispenser<NetFlow.DemandChanged>

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _notificationChl: Channel<Notification<NetFlow>> = InvalidatorChl(this)
    override val notificationChl: SendChannel<Notification<NetFlow>> = _notificationChl
    private val _priorityNotificationChl: Channel<Notification<NetFlow>> = InvalidatorChl(this)
    override val priorityNotificationChl: SendChannel<Notification<NetFlow>> = _priorityNotificationChl

    private lateinit var setDemandNotifDispenser: FWDispenser<NetFlow.SetDemand>
    private lateinit var setTputNotifDispenser: FWDispenser<INetFlow.SetThroughput>
    private lateinit var increaseTputNotifDispenser: FWDispenser<INetFlow.IncreaseThroughput>

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
        ): NetFlowV1 = NetFlowV1(
            senderId = senderId,
            destId = destId,
            id = id ?: idDispenser.getFlowId(),
            demand = demand,
            stabilizer = barrier.stabilizer(),
        ).also {
            // Set event dispensers.
            it.tputChangedEvntDispenser = dispenser(NetFlow.ThroughputChanged)
            it.fragCompletedEvntDispenser = dispenser(NetFlow.FragmentCompleted)
            it.demandChangedEvntDispenser = dispenser(NetFlow.DemandChanged)

            // Set notification dispensers.
            it.setDemandNotifDispenser = dispenser(NetFlow.SetDemand)
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Events
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        context(NetSimScope) override suspend fun dispenser(
            id: FWId<NetFlow.DemandChanged>
        ): FWDispenser<NetFlow.DemandChanged> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : NetFlow.DemandChanged, IFW<NetFlow.DemandChanged> {
                    override val pool: FWPool<NetFlow.DemandChanged, FWId<NetFlow.DemandChanged>> = pool
                    override val poolIdx: Idx = idx
                    override var old: DataRate = DataRate.zero
                    override var new: DataRate = DataRate.zero
                    override lateinit var netFlow: NetFlow
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FWId<NetFlow.FragmentCompleted>
        ): FWDispenser<NetFlow.FragmentCompleted> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : NetFlow.FragmentCompleted, IFW<NetFlow.FragmentCompleted> {
                    override val pool: FWPool<NetFlow.FragmentCompleted, FWId<NetFlow.FragmentCompleted>> = pool
                    override val poolIdx: Idx = idx
                    override lateinit var netFlow: NetFlow
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FWId<NetFlow.ThroughputChanged>
        ): FWDispenser<NetFlow.ThroughputChanged> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : NetFlow.ThroughputChanged, IFW<NetFlow.ThroughputChanged> {
                    override val pool: FWPool<NetFlow.ThroughputChanged, FWId<NetFlow.ThroughputChanged>> = pool
                    override val poolIdx: Idx = idx
                    override var old: DataRate = DataRate.zero
                    override var new: DataRate = DataRate.zero
                    override lateinit var netFlow: NetFlow
                }
            }.dispenser()

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        context(NetSimScope) override suspend fun dispenser(
            id: FWId<NetFlow.SetDemand>
        ): FWDispenser<NetFlow.SetDemand> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : NetFlow.SetDemand, IFW<NetFlow.SetDemand>, IInvalidatable {
                    override val pool: FWPool<NetFlow.SetDemand, FWId<NetFlow.SetDemand>> = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab
                    override var newDemand: DataRate = DataRate.zero

                    context(NetFlow)
                    override suspend fun handle() {
                        val f = this@NetFlow as NetFlowV1
                        val old: DataRate = demand
                        f.demand = newDemand
                        val evnt = f.demandChangedEvntDispenser.acquire()
                        evnt.netFlow = f
                        evnt.old = old
                        evnt.new = demand
                        f._eventFlow.emit(evnt)
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FWId<INetFlow.SetThroughput>
        ): FWDispenser<INetFlow.SetThroughput> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : INetFlow.SetThroughput, IInvalidatable {
                    override val stabilizer: NetSimStabilizer = stab
                    override val pool: FWPool<INetFlow.SetThroughput, FWId<INetFlow.SetThroughput>> = pool
                    override val poolIdx: Idx = idx
                    override var newThroughput: DataRate = DataRate.zero

                    context(NetFlow)
                    override suspend fun handle() {
                        val f = this@NetFlow as NetFlowV1
                        val old: DataRate = throughput
                        f.throughput = newThroughput
                        val evnt = f.tputChangedEvntDispenser.acquire()
                        evnt.old = old
                        evnt.new = throughput
                        evnt.netFlow = f
                        f._eventFlow.emit(evnt)
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(
            id: FWId<INetFlow.IncreaseThroughput>
        ): FWDispenser<INetFlow.IncreaseThroughput> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : INetFlow.IncreaseThroughput, IInvalidatable {
                    override val pool: FWPool<INetFlow.IncreaseThroughput, FWId<INetFlow.IncreaseThroughput>> = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab
                    override var amount: DataRate = DataRate.zero

                    context(NetFlow)
                    override suspend fun handle() {
                        val f = this@NetFlow as NetFlowV1
                        val old: DataRate = throughput
                        f.throughput += amount
                        val evnt = f.tputChangedEvntDispenser.acquire()
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
