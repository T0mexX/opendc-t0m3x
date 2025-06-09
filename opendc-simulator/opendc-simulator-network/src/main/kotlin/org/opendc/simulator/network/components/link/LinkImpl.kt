package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.PortFlowEntry
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.datastructures.IntArrayQueue
import org.opendc.simulator.network.utils.notifiable.Msg


internal class LinkImpl private constructor(
    override val receiverN: Node<*>,
    override val maxBw: DataRate,
    override val stabilizer: NetSimStabilizer,
    override val linkIdx: Int,
    initialCapacity: Int,
): Link, SendChannel<Msg<Node<*>, *>> by receiverN.msgChl {
    private var usedBw: DataRate = DataRate.zero
    override val availableBw: DataRate get() = maxBw - usedBw
    override val util: Percentage get() = usedBw / maxBw

    override var totTentativeTx: DataRate = DataRate.zero

    /**
     * Ensure mutual exclusion between the process of
     * attempting tx and to set the tentative tx.
     */
    private val mtx = Mutex()

    override suspend fun attemptTx() = mtx.withLock {
        assert(usedBw approxSmallerOrEq maxBw)

        coroutineScope {
            entries.asFlow().let { f ->
                // Apply data-rate reductions.
                f.onEach { e ->
                    if (e.used.not()) return@onEach
                    var delta = (  (maxBw * (e.demand / totTentativeTx)  ) min e.demand) - e.tput
                    delta = delta.roundToIfWithinEpsilon(DataRate.zero, 1.0)
                    if (delta approxLargerOrEq DataRate.zero) return@onEach
                    usedBw = (usedBw + delta).roundToIfWithinEpsilon(DataRate.zero, 1.0)
                    e.tput = (e.tput + delta).roundToIfWithinEpsilon(e.demand, 1.0)
                    receiverN.msgAsyncRxUpdt(deltaRate = delta, f = e.netF)
                }.launchIn(this@coroutineScope).join()

                // Apply data-rate increases.
                f.onEach { e ->
                    if (e.used.not()) return@onEach
                    val delta = (  (maxBw * (e.demand / totTentativeTx)  ) min e.demand) - e.tput
                    if (delta <= DataRate.zero && e.demand == DataRate.zero) return@onEach rmEntry(e.idx)
                    if (delta approxSmallerOrEq DataRate.zero) return@onEach
                    usedBw = (usedBw + delta).roundToIfWithinEpsilon(maxBw, 1.0)
                    e.tput = (e.tput + delta).roundToIfWithinEpsilon(e.demand, 1.0)
                    receiverN.msgAsyncRxUpdt(deltaRate = delta, f = e.netF)
                }.launchIn(this@coroutineScope).join()
            }
        }

        stabilizer.validate()
        assert(usedBw approxSmallerOrEq maxBw) { "usedBw:$usedBw  maxBw:$maxBw" }
    }

    override suspend fun setTentativeTx(dr: DataRate, f: INetFlow, entryId: Int?): Int = mtx.withLock {
        assert(dr >= DataRate.zero)

        // Invalidate the port until `attemptTx`
        // is called and updates are propagated to `receiverN`.
        stabilizer.invalidate()

        @Suppress("NAME_SHADOWING")
        val entryId = entryId ?: newEntry()
        val entry = entries[entryId]
        assert(entry.used)

        entry.netF = f
        totTentativeTx += dr - entry.demand
        entry.demand = dr

        return entryId
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Link Internal Implementation
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

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(senderN: Node<*>, receiverN: Node<*>, linkIdx: Int): Link =
            LinkImpl(
                receiverN = receiverN,
                maxBw = senderN.portSpeed min receiverN.portSpeed,
                stabilizer = barrier.stabilizer(),
                linkIdx = linkIdx,
                initialCapacity = devConfig.linkConfig.initialCapacity,
            )
    }

//    context(FairnessPolicy)
//    override suspend fun claimBw(bw: DataRate, netF: INetFlow): DataRate = mtx.withLock {
//        assert(bw != DataRate.zero)
//
//        // If bandwidth to be claimed is approximately 0, then do not propagate update.
//        if (bw.approx(DataRate.zero, epsilon = 1.0)) return@withLock DataRate.zero
//
//        // The currently available bandwidth on the link.
//        val available: DataRate = maxBw - usedBw
//
//        // The additional bandwidth that will be taken by `netF`.
//        val deltaBw =
//            if (bw > available) available
//            else bw
//
//        if (deltaBw approx DataRate.zero) return DataRate.zero
//
//        // Update the currently used bandwidth on the link, rounding to max if necessary.
//        usedBw = (usedBw + deltaBw).roundToIfWithinEpsilon(maxBw, 1.0)
//
//        // Send update to the receiver node.
//        receiverPort.owner.msgAsyncRxUpdt(deltaBw, netF)
//
//        return deltaBw
//    }
//
//    context(FairnessPolicy)
//    override suspend fun releaseBw(bw: DataRate, netF: INetFlow) = mtx.withLock {
//        assert(bw approxSmallerOrEq usedBw && bw > DataRate.zero)
//
//        // If the bandwidth to be released is approximately 0, then do not propagate update.
//        if (bw.approx(DataRate.zero, epsilon = 1.0)) return@withLock
//
//        // Update currently used bandwidth on the link, routing to 0 if necessary
//        usedBw = (usedBw - bw).roundToIfWithinEpsilon(DataRate.zero, epsilon = 1.0)
//
//        // Send update to the receiver node.
//        receiverPort.owner.msgAsyncRxUpdt(-bw, netF)
//    }
}
