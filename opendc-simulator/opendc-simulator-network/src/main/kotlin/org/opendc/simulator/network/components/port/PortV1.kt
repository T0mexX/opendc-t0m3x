package org.opendc.simulator.network.components.port

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink2
import org.opendc.simulator.network.components.link.SendLink2
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.FlowId2
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.pool.DeltaFlowPool
import org.opendc.simulator.network.utils.notifiable.Notification
import org.opendc.simulator.network.utils.statefull.State
import org.opendc.simulator.network.sync.flyweight.PoolIdx
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.IntId
import org.opendc.simulator.network.utils.datastructures.IntArrayQueue

internal class PortV1 private constructor(
    private val poolIdx: PoolIdx,
    private val pool: DeltaFlowPool,
    startingListSize: Int,
): Port {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ChlNotifiable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val notificationChl: ReceiveChannel<org.opendc.simulator.network.utils.notifiable.Notification<Port>> get() = _notificationChl
    private val _notificationChl = Channel<org.opendc.simulator.network.utils.notifiable.Notification<Port>>(Channel.UNLIMITED)
    override val PROCESS: org.opendc.simulator.network.utils.notifiable.Notification<PortV1> get() = Companion.PROCESS

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stateful
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val state: StateFlow<org.opendc.simulator.network.utils.statefull.State<Port>> get() = _state
    private val _state = MutableStateFlow(Port.STABLE)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Port Interface Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override var txLink: SendLink2? = null
    override var rxLink: ReceiveLink2? = null
    override var fairnessPolicy: FairnessPolicy = MaxMinPerPort
    override fun setTxDemand(txDemand: DataRate, entryId: IntId?): IntId {
        if (txDemand.isZero()) return rmEntry(entryId!!)

        return entryId?.let {
            entries[it].txDemand = txDemand
            entryId
        } ?: newEntry(txDemand)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Port Internal Implementation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val entries: MutableList<PortFlowEntry> = 0.rangeUntil(startingListSize).map {
        PortFlowEntry()
    }.toMutableList()

    private val freeIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = startingListSize).also { q ->
        (0 until startingListSize).forEach { q.add(it) }
    }

    private val toRmIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = startingListSize).also { q ->
        (0 until startingListSize).forEach { q.add(it) }
    }

    private val changedIdxs: IntArrayQueue = IntArrayQueue(initialCapacity = startingListSize).also { q ->
        (0 until startingListSize).forEach { q.add(it) }
    }

    private fun newEntry(txDemand: DataRate): Idx {
        val idx: Idx = freeIdxs.poll()
            ?: let {
                grow()
                freeIdxs.poll()
            }
        entries[idx].also {
            it.used = true
            it.txDemand = txDemand
        }
        return idx
    }

    private fun grow() {
        entries.addAll((0 until entries.size).map { PortFlowEntry() })
    }

    private fun rmEntry(idx: Idx): Idx {
        toRmIdxs.add(idx)
        return -1
    }

    private data class PortFlowEntry(
        var used: Boolean = false,
        var flowId: FlowId2 = FlowId2.INVALID,
        var txDemand: DataRate = DataRate.zero,
        var txThroughput: DataRate = DataRate.zero
    )

    companion object {
        context(NetSimScope, Node)
        suspend operator fun invoke() =
            PortV1(
                pool = deltaFlowPool,
                poolIdx = deltaFlowPool.getIdx(),
                startingListSize = devConfig.startingListsSize,
            )

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Notifications
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private val PROCESS: org.opendc.simulator.network.utils.notifiable.Notification<PortV1> =
            org.opendc.simulator.network.utils.notifiable.Notification {
                _state.emit(Port.PROCESSING)
                TODO()
            }
    }
}

