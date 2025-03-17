package org.opendc.simulator.network.components.port

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink2
import org.opendc.simulator.network.components.link.SendLink2
import org.opendc.simulator.network.flow.neww.publics.FlowId2
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.notifiable.publics.Notification
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.IntId
import org.opendc.simulator.network.utils.IntSz
import org.opendc.simulator.network.utils.datastructures.IntArrayQueue
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.statefull.publics.State

internal class PortV1 private constructor(
    initialCapacity: IntSz,
    override val stabilizer: NetSimStabilizer
): Port {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Port
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override var txLink: SendLink2? = null
    override var rxLink: ReceiveLink2? = null
    override var fairnessPolicy: FairnessPolicy = MaxMinPerPort

    override suspend fun startProcessing() {
        TODO("Not yet implemented")
    }

    override suspend fun setTxDemand(txDemand: DataRate, entryId: IntId?): IntId {
        val notif = setDemandNotifDispenser.acquire()
        notif.newDemand = txDemand
        val id = entryId ?: newEntry()
        notif.entryId = id
        _notificationChl.send(notif)
        return id
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope) override fun netLaunch(): Job = this@NetSimScope.scopeLaunch {
        while (isActive) {
            _notificationChl.receive().handle()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var _notificationChl = InvalidatorChl<Notification<Port>>(this)
    override val notificationChl: SendChannel<Notification<Port>> = _notificationChl

    private lateinit var startProcessingNotifDispenser: FWDispenser<Port.StartProcessing>
    private lateinit var setDemandNotifDispenser: FWDispenser<Port.SetDemand>

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stateful
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var _state = MutableStateFlow(Port.STABLE)
    override val state: StateFlow<State<Port>> = _state

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Port Internal Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val entries: MutableList<PortFlowEntry> = 0.rangeUntil(initialCapacity).map {
        PortFlowEntry()
    }.toMutableList()

    private val freeIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = initialCapacity).also { q ->
        (0 until initialCapacity).forEach { q.add(it) }
    }

    private val toRmIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = initialCapacity).also { q ->
        (0 until initialCapacity).forEach { q.add(it) }
    }

//    private val toProcessIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = initialCapacity).also { q ->
//        (0 until initialCapacity).forEach { q.add(it) }
//    }

    private fun newEntry(): Idx =
        freeIdxs.poll()
            ?: grow1()

    private fun grow1(): Idx {
        entries.add(PortFlowEntry().also { it.used = true })
        return entries.size - 1
    }

    private fun rmEntry(idx: Idx) {
        toRmIdxs.add(idx)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PortVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    companion object : PortVersion {

        context(NetSimScope)
        override suspend operator fun invoke() =
            PortV1(
                initialCapacity = devConfig.portConfig.initialCapacity,
                stabilizer = barrier.stabilizer()
            ).also {
                it.startProcessingNotifDispenser = dispenser(Port.StartProcessing)
                it.setDemandNotifDispenser = dispenser(Port.SetDemand)
            }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        context(NetSimScope) override suspend fun dispenser(id: FlyWeightId<Port.StartProcessing>): FWDispenser<Port.StartProcessing> =
            poolAggr.getCreatePool(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : Port.StartProcessing, IFW<Port.StartProcessing>, Invalidatable {
                    override val pool: FWPool<Port.StartProcessing, FlyWeightId<Port.StartProcessing>> = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab

                    context(Port) override suspend fun handle() {
                        val p = this@Port as PortV1
                        TODO()
                    }
                }
            }.dispenser()

        context(NetSimScope) override suspend fun dispenser(id: FlyWeightId<Port.SetDemand>): FWDispenser<Port.SetDemand> =
            poolAggr.getCreatePool(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : Port.SetDemand, IFW<Port.SetDemand>, Invalidatable {
                    override val pool: FWPool<Port.SetDemand, FlyWeightId<Port.SetDemand>> = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab
                    override var newDemand: DataRate = DataRate.zero
                    override var entryId: IntId = -1

                    context(Port) override suspend fun handle() {
                        val p = this@Port as PortV1
                        if (newDemand.isZero()) return p.rmEntry(entryId)
                        val entry = p.entries[entryId]
                        val oldDemand = entry.txDemand
                        entry.txDemand = newDemand
                        if (newDemand < oldDemand && txTh) {
                            p.txLink!!.releaseBw(oldDemand - newDemand)
                            entry.txThroughput = newDemand min
                        }
                        else p.toProcessIdxs.add(entryId)
                        dispose()
                    }
                }
            }.dispenser()
    }
}

