package org.opendc.simulator.network.components.port

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.components.link.SimplexLink
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.notifiable.MsgImpl
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.IntId
import org.opendc.simulator.network.utils.IntSz
import org.opendc.simulator.network.utils.datastructures.IntArrayQueue
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.statefull.State
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

internal class PortV1 private constructor(
    override val owner: Node<*>,
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

    override suspend fun msgProcess() {
        TODO("Not yet implemented")
    }

    override suspend fun msgSetTxDemand(txDemand: DataRate, netF: INetFlow, entryId: IntId?): IntId? {
        require(txDemand >= DataRate.zero)
        val msg = setDemandDisp.acquire().reset()
        msg.newDemand = txDemand
        msg.entryId = entryId
        msg.netF = netF
        msg.sendTo(this, dispose = false).awaitHandling()
        return msg.entryId.also { msg.dispose() }
    }

    override fun getTxTput(entryId: IntId): DataRate {
        assert(stabilizer.isValidated)

        return entries[entryId].tput
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Launchable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    context(NetSimScope)
    override suspend fun netLaunch(scope: CoroutineScope): Job = scope.launch(CoroutineID.new()) {
        while (isActive) {
            _msgChl.receive().also {
                assert(stabilizer.isValidated.not())
            }.handle()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Msgable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val msgChl: SendChannel<Msg<Port, *>> get() = _msgChl
    private var _msgChl = InvalidatorChl<Msg<Port, *>>(this)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stateful
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var _state = MutableStateFlow(Port.DISCONNECTED)
    override val state: StateFlow<State<Port>> = _state

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Port Internal Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val entries: MutableList<PortFlowEntry> = 0.rangeUntil(initialCapacity).map {
        PortFlowEntry(it)
    }.toMutableList()

    private val freeIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = initialCapacity).also { q ->
        (0 until initialCapacity).forEach { q.add(it) }
    }

    private fun newEntry(): Idx =
        let {
            freeIdxs.poll() ?: grow1()
        }.also { idx -> entries[idx].used = true }

    private fun grow1(): Idx {
        entries.add(PortFlowEntry(idx = entries.size - 1))
        return entries.size - 1
    }

    private fun rmEntry(idx: Idx) {
        freeIdxs.add(idx)
        entries[idx].used = false
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PortVersion
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    companion object : PortVersion {

        context(NetSimScope)
        override suspend operator fun invoke(owner: Node<*>, portIdx: Idx) =
            PortV1(
                owner = owner,
                portIdx = portIdx,
                initialCapacity = devConfig.portConfig.initialCapacity,
                stabilizer = barrier.stabilizer()
            ).also {
                it.invalidate()
            }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications Dispensers
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        override val startProcessingDisp: FWDispenser<Port.Process> get() = _startProcessingDisp
        private lateinit var _startProcessingDisp: FWDispenser<Port.Process>

        override val setDemandDisp: FWDispenser<Port.SetDemand> get() = _setDemandDisp
        private lateinit var _setDemandDisp: FWDispenser<Port.SetDemand>

        override val connectDisp: FWDispenser<Port.Connect> get() = _connectDisp
        private lateinit var _connectDisp: FWDispenser<Port.Connect>

        override val disconnectDisp: FWDispenser<Port.Disconnect> get() = _disconnectDisp
        private lateinit var _disconnectDisp: FWDispenser<Port.Disconnect>

        context(NetSimScope)
        override suspend fun initDispensers() {
            _startProcessingDisp =
                poolAggr.getOrAdd(Port.Process as FWId<Port.Process>) { pool, idx ->
                    val stab = barrier.stabilizer()
                    object : Port.Process, IInvalidatable, MsgImpl<Port, Port.Process>(pool, idx) {
                        override val stabilizer: NetSimStabilizer = stab

                        context(Port) override suspend fun handle() {
                            val p = this@Port as PortV1

                            // If port disconnected no processing needed.
                            if (p._state.value == Port.DISCONNECTED) return handled()

                            // Apply fairness policy and propagate updates to the connected node.
                            p._state.emit(Port.PROCESSING)
                            p.owner.fairnessPolicy.applyFairness(p.entries)

                            // Remove entries with 0 demand after updates propagated to connected node.
                            coroutineScope {
                                p.entries.asFlow().onEach { e ->
                                    if (e.used && e.demand == DataRate.zero) p.rmEntry(e.idx)
                                }.launchIn(this@coroutineScope)
                            }

                            // Update port state.
                            if (p.freeIdxs.getSize() == p.entries.size) {
                                p._state.emit(Port.IDLE)
                            } else {
                                p._state.emit(Port.STABLE)
                            }

                            handled()
                        }
                    }
                }



            _setDemandDisp =
                poolAggr.getOrAdd(Port.SetDemand as FWId<Port.SetDemand>) { pool, idx ->
                val stab = barrier.stabilizer()
                object : Port.SetDemand, IInvalidatable, MsgImpl<Port, Port.SetDemand>(pool, idx) {
                    override val stabilizer: NetSimStabilizer = stab
                    override var newDemand: DataRate = DataRate.zero
                    override var entryId: IntId? = null
                    override lateinit var netF: INetFlow

                    context(Port) override suspend fun handle() {
                        assert(newDemand >= DataRate.zero)
                        val p = this@Port as PortV1

                        entryId = entryId ?: p.newEntry()
                        val entry = p.entries[entryId!!]
                        assert(entry.used)
                        entry.netF = netF
                        entry.demand = newDemand

                        handled()
                    }
                }
            }


            _connectDisp =
                poolAggr.getOrAdd(Port.Connect as FWId<Port.Connect>) { pool, idx ->
                    object : Port.Connect, MsgImpl<Port, Port.Connect>(pool, idx)  {
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

                            handled()
                        }
                    }
                }



            _disconnectDisp =
                poolAggr.getOrAdd(Port.Disconnect as FWId<Port.Disconnect>) { pool, idx ->
                    object : Port.Disconnect, MsgImpl<Port, Port.Disconnect>(pool, idx) {
                        context(Port) override suspend fun handle() {
                            val p = this@Port as PortV1
                            p._state.emit(Port.DISCONNECTING)
                            require(p.txLink != null) { "unable to disconnect port $this, port not connected" }

                            p.entries.forEach {

                                TODO("change")
                                val msg = nodeVersion.rxUpdateDisp.acquire()
                                msg.netF = it.netF
                                msg.deltaRate = -it.tput
                                p.txLink!!.send(msg)
                            }

                            txLink = null
                            rxLink = null
                            p._state.emit(Port.DISCONNECTED)

                            handled()
                        }
                    }
                }
        }
    }
}

