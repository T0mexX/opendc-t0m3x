@file:OptIn(InternalOdcNetworkApi::class)

package org.opendc.simulator.network.flow

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.NetFlow
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.InternalOdcNetworkApi
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.eventEmitter.Event
import org.opendc.simulator.network.utils.flyweight.FlyWeightDispenser
import org.opendc.simulator.network.utils.flyweight.FlyWeightPool
import org.opendc.simulator.network.utils.flyweight.IFlyWait
import org.opendc.simulator.network.utils.invalidatable.Invalidatable
import org.opendc.simulator.network.utils.invalidatable.InvalidatorChl
import org.opendc.simulator.network.utils.invalidatable.InvalidatorFlow
import org.opendc.simulator.network.utils.invalidatable.MutableInvalidatorFlow
import org.opendc.simulator.network.utils.notifiable.Notification

internal class INetFlow private constructor(
    override val senderId: NodeId2,
    override val destId: NodeId2,
    override val id: FlowId2,
    demand: DataRate,
    override val stabilizer: NetSimStabilizer,
): NetFlow, Invalidatable, Launchable {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFlow
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope) override fun netLaunch(): Job = scopeLaunch {
        TODO()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _eventFlow: MutableInvalidatorFlow<Event<NetFlow>> = MutableInvalidatorFlow()
    override val eventFlow: InvalidatorFlow<Event<NetFlow>> = _eventFlow

    private lateinit var tputEvntDispenser: FlyWeightDispenser<TputChng>
    private data class TputChng(
        override val pool: FlyWeightPool<NetFlow.ThroughPutChangedEvent>,
        override val poolIdx: Idx,
        override var old: DataRate = DataRate.zero,
        override var new: DataRate = DataRate.zero,
    ): NetFlow.ThroughPutChangedEvent, IFlyWait<NetFlow.ThroughPutChangedEvent>, Event<NetFlow> {
        override val netFlow: INetFlow get() = netF!!
        var netF: INetFlow? = null
    }

    private lateinit var fragEvntDispenser: FlyWeightDispenser<FragCompl>
    private data class FragCompl(
        override val pool: FlyWeightPool<NetFlow.FragmentCompleted>,
        override val poolIdx: Idx,
    ): NetFlow.FragmentCompleted, IFlyWait<NetFlow.FragmentCompleted>, Event<NetFlow> {
        override val netFlow: INetFlow get() = netF!!
        var netF: INetFlow? = null
    }

    private lateinit var demandChngEvntDispenser: FlyWeightDispenser<DemandChng>
    private data class DemandChng(
        override val pool: FlyWeightPool<NetFlow.DemandChanged>,
        override val poolIdx: Idx,
        override var old: DataRate = DataRate.zero,
        override var new: DataRate = DataRate.zero,
    ): NetFlow.DemandChanged, IFlyWait<NetFlow.DemandChanged>, Event<NetFlow> {
        override val netFlow: INetFlow get() = netF!!
        var netF: INetFlow? = null
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val _notificationChl: Channel<Notification<NetFlow>> = InvalidatorChl(this)
    override val notificationChl: SendChannel<Notification<NetFlow>> = _notificationChl

    internal class TputIncreaseBy private constructor(
        override val pool: FlyWeightPool<TputIncreaseBy>,
        override val poolIdx: Idx,
        override val stabilizer: NetSimStabilizer,
        internal val amount: DataRate = DataRate.zero,
    ): IFlyWait<TputIncreaseBy>, Invalidatable, Notification<NetFlow> {
        override suspend fun NetFlow.handle() {
            this as INetFlow
            val old: DataRate = throughput
            throughput += amount
            val evnt = tputEvntDispenser.acquire()
            evnt.netF = this
            evnt.old = old
            evnt.new = throughput
            _eventFlow.emit(evnt)
            this@TputIncreaseBy.dispose()
        }
    }

    private lateinit var setDemandNotifDispenser: FlyWeightDispenser<SetDemand>
    internal class SetDemand private constructor(
        override val pool: FlyWeightPool<SetDemand>,
        override val poolIdx: Idx,
        override val stabilizer: NetSimStabilizer,
        internal var newDemand: DataRate = DataRate.zero,
    ): IFlyWait<SetDemand>, Invalidatable, Notification<NetFlow> {
        override suspend fun NetFlow.handle() {
            this as INetFlow
            val old: DataRate = demand
            demand = newDemand
            val evnt = demandChngEvntDispenser.acquire()
            evnt.netF = this
            evnt.old = old
            evnt.new = demand
            _eventFlow.emit(evnt)
            this@SetDemand.dispose()
        }
    }


    internal companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            senderId: NodeId2,
            destId: NodeId2,
            id: FlowId2? = null,
            demand: DataRate = DataRate.zero,
        ): INetFlow = INetFlow(
            senderId = senderId,
            destId = destId,
            id = id ?: idDispenser.getFlowId(),
            demand = demand,
            stabilizer = barrier.stabilizer(),
        ).also {
            it.tputEvntDispenser = pool.getCreatePool { idx ->
                TputChng(pool.getPool(), idx)
            }.dispenser()
            it.fragEvntDispenser = pool.getCreatePool { idx ->
                FragCompl(pool.getPool(), idx)
            }.dispenser()
            it.demandChngEvntDispenser = pool.getCreatePool { idx ->
                DemandChng(pool.getPool(), idx)
            }.dispenser()
        }
    }
}
