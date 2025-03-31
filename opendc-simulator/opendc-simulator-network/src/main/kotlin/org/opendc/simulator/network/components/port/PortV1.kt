package org.opendc.simulator.network.components.port

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.components.link.SimplexLink
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
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
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.statefull.publics.State

internal class PortV1 private constructor(
    override val owner: Node,
    override val portIdx: Idx,
    initialCapacity: IntSz,
    override val stabilizer: NetSimStabilizer
): Port {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Port
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val speed: DataRate = owner.portSpeed
    override var txLink: SendLink? = null
    override var rxLink: ReceiveLink? = null
    override lateinit var fairnessPolicy: FairnessPolicy

    override suspend fun startProcessing() {
        TODO("Not yet implemented")
    }

    override suspend fun setTxDemand(txDemand: DataRate, netFlow: NetFlow, entryId: IntId?): IntId {
        val notif = setDemandDisp.acquire()
        notif.newDemand = txDemand
        val id = entryId ?: newEntry()
        notif.entryId = id
        notif.netFlow = netFlow
        _notificationChl.send(notif)
        return id
    }

    override fun getTxTput(entryId: IntId): DataRate {
//        _state.first { it == Port.STABLE }
        return entries[entryId].tput
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
    private val _priorityNotificationChl = InvalidatorChl<Notification<Port>>(this)
    override val priorityNotificationChl: SendChannel<Notification<Port>> = _priorityNotificationChl

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stateful
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var _state = MutableStateFlow(Port.DISCONNECTED)
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

    private fun newEntry(): Idx =
        freeIdxs.poll()
            ?: grow1()

    private fun grow1(): Idx {
        entries.add(PortFlowEntry().also { it.used = true })
        return entries.size - 1
    }

    private fun rmEntry(idx: Idx) {
        freeIdxs.add(idx)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PortVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    companion object : PortVersion {

        context(NetSimScope)
        override suspend operator fun invoke(owner: Node, portIdx: Idx) =
            PortV1(
                owner = owner,
                portIdx = portIdx,
                initialCapacity = devConfig.portConfig.initialCapacity,
                stabilizer = barrier.stabilizer()
            )

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications Dispensers
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        override val startProcessingDisp: FWDispenser<Port.StartProcessing> get() = _startProcessingDisp
        private lateinit var _startProcessingDisp: FWDispenser<Port.StartProcessing>

        override val setDemandDisp: FWDispenser<Port.SetDemand> get() = _setDemandDisp
        private lateinit var _setDemandDisp: FWDispenser<Port.SetDemand>

        override val connectDisp: FWDispenser<Port.Connect> get() = _connectDisp
        private lateinit var _connectDisp: FWDispenser<Port.Connect>

        override val disconnectDisp: FWDispenser<Port.Disconnect> get() = _disconnectDisp
        private lateinit var _disconnectDisp: FWDispenser<Port.Disconnect>

        context(NetSimScope)
        override suspend fun initDispensers() {
            _startProcessingDisp = dispenser(Port.StartProcessing)
            _setDemandDisp = dispenser(Port.SetDemand)
            _connectDisp = dispenser(Port.Connect)
            _disconnectDisp = dispenser(Port.Disconnect)
        }


        context(NetSimScope) private suspend fun dispenser(id: FWId<Port.StartProcessing>): FWDispenser<Port.StartProcessing> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : Port.StartProcessing, IFW<Port.StartProcessing>, IInvalidatable {
                    override val pool = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab

                    context(Port) override suspend fun handle() {
                        val p = this@Port as PortV1
                        p._state.emit(Port.PROCESSING)
                        p.owner.fairnessPolicy.applyPolicy(p.entries, reductionsToBeExecuted = false)
                        if (p.freeIdxs.getSize() == p.entries.size) {
                            p._state.emit(Port.IDLE)
                        } else {
                            p._state.emit(Port.STABLE)
                        }
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(id: FWId<Port.SetDemand>): FWDispenser<Port.SetDemand> =
            poolAggr.getOrAdd(id) { pool, idx ->
                val stab = barrier.stabilizer()
                object : Port.SetDemand, IFW<Port.SetDemand>, IInvalidatable {
                    override val pool: FWPool<Port.SetDemand, FWId<Port.SetDemand>> = pool
                    override val poolIdx: Idx = idx
                    override val stabilizer: NetSimStabilizer = stab
                    override var newDemand: DataRate = DataRate.zero
                    override var entryId: IntId = -1
                    override lateinit var netFlow: NetFlow

                    context(Port) override suspend fun handle() {
                        val p = this@Port as PortV1
                        if (newDemand.isZero()) return p.rmEntry(entryId)
                        val entry = p.entries[entryId]
                        val oldDemand = entry.demand
                        entry.demand = newDemand
                        if (newDemand < oldDemand && entry.tput > newDemand) {
                            p.txLink!!.releaseBw(oldDemand - newDemand)
                            entry.tput = newDemand
                        }
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(id: FWId<Port.Connect>): FWDispenser<Port.Connect> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Port.Connect {
                    override val pool = pool
                    override val poolIdx: Idx = idx
                    override lateinit var other: Port
                    override var linkBw: DataRate? = DataRate.zero

                    context(Port) override suspend fun handle() {
                        val p = this@Port as PortV1
                        p._state.emit(Port.CONNECTING)

                        require(p.txLink == null) {
                            "unable to connect ports $this and $other. $this is already connected"
                        }

                        val computedLinkBW: DataRate = linkBw ?: (p.speed min other.speed)

                        val thisToOther = SimplexLink(other, maxBw = computedLinkBW)
                        p.txLink = thisToOther
                        other.rxLink = thisToOther

                        if (p.freeIdxs.getSize() == p.entries.size) {
                            p._state.emit(Port.IDLE)
                        } else {
                            p._state.emit(Port.STABLE)
                        }
                        dispose()
                    }
                }
            }.dispenser()

        context(NetSimScope) private suspend fun dispenser(id: FWId<Port.Disconnect>): FWDispenser<Port.Disconnect> =
            poolAggr.getOrAdd(id) { pool, idx ->
                object : Port.Disconnect {
                    override val pool = pool
                    override val poolIdx = idx

                    context(Port) override suspend fun handle() {
                        val p = this@Port as PortV1
                        p._state.emit(Port.DISCONNECTING)
                        require(p.txLink != null) { "unable to disconnect port $this, port not connected" }

                        p.entries.forEach {
                            val notif = nodeVersion.rxUpdateDisp.acquire()
                            notif.netFlow = it.netFlow
                            notif.deltaRate = -it.tput
                            p.txLink!!.send(notif)
                        }

                        txLink = null
                        rxLink = null
                        p._state.emit(Port.DISCONNECTED)

                        dispose()
                    }
                }
            }.dispenser()
    }
}

