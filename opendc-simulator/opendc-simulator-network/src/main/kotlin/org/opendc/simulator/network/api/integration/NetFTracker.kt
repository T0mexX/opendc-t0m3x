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

package org.opendc.simulator.network.api.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NetRunnable
import org.opendc.simulator.network.components.evntemitter.EvntListener
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NetCoId
import org.opendc.simulator.network.utils.SetOnce
import kotlin.coroutines.CoroutineContext

public class NetFTracker private constructor(
    private val flows: List<INetFlow>,
    private val listeners: List<EvntListener<NetFlow>>,
) : AutoCloseable, NetRunnable {
    private var remaining: Int = flows.size
    private val estimateComplTs = flows.map { Timestamp.max }.toMutableList()

    private var latestTsIdx: Int = 0
    private var earliestTsIdx: Int = 0

    public var onAllComplTsIncreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var onAllComplTsDecreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var on1ComplTsIncreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var on1ComplTsDecreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var on1FFragCompl: suspend (f: NetFlow, fId: Any?) -> Unit = { _, _ -> }
    public var onAllFFragCompl: suspend () -> Unit = {}
    private val mtx = Mutex()
    private var fragCount: Int = 0

    @ProtectedUse override var job: Job by SetOnce()

    /**
     * @return The estimated earliest timestamp at which a flow will complete its fragment.
     */
    public suspend fun tsFor1Compl(): Timestamp =
        mtx.withLock {
            estimateComplTs[earliestTsIdx]
        }

    /**
     * @return The estimated time remaining until a flow completes its fragment.
     */
    context(NetSimScope)
    public suspend fun tmRmFor1Compl(): TimeDelta =
        tsFor1Compl() timeDelta tmSrc.tmstamp

    /**
     * @return The estimated timestamp at which all flows will have completed their fragments.
     */
    public suspend fun tsForAllCompl(): Timestamp =
        mtx.withLock {
            estimateComplTs[latestTsIdx]
        }

    /**
     * @return The estimated time remaining until all flows complete their fragment.
     */
    context(NetSimScope)
    public suspend fun tmRmForAllCompl(): TimeDelta =
        tsForAllCompl() timeDelta tmSrc.tmstamp

    /**
     * TODO
     * To be called after flows have been msged with the new frag msg.
     */
    public suspend fun reset(): Unit =
        mtx.withLock {
            remaining = flows.size
            latestTsIdx = 0
            estimateComplTs.indices.forEach { i ->
                val f = flows[i]
                estimateComplTs[i] = f.msgSyncReqFragComplEstimate()
                if (estimateComplTs[i] > estimateComplTs[latestTsIdx]) latestTsIdx = i
            }
            fragCount++
        }

    context(NetSimScope)
    @ProtectedUse
    override fun netRun(additionalCtx: CoroutineContext) {
        job =
            launch(additionalCtx) {
                try {
                    while (coroutineContext.isActive) {
                        handle1()
                    }
                } finally {
                    drainListeners()
                }
            }
    }

//    context(NetSimCtxOld)
//    private suspend fun run() {
//        try {
//            while (coroutineContext.isActive) {
//                handle1()
//            }
//        } finally { drainListeners() }
//    }

    context(NetSimScope)
    private suspend fun handle1() {
        select {
            listeners.onEachIndexed { idx, l ->
                l.onReceive { e ->
                    try {
                        mtx.withLock {
                            when (e) {
                                is NetFlow.TPutChanged -> handleTputChange(e, idx)
                                is NetFlow.FragCompl -> handleFragCompl(e, idx)
                                is NetFlow.FragComplEstimateChanged -> handleFragComplEstimateChanged(e, idx)
                            }
                        }
                    } finally {
                        e.handled()
                    }
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun drainListeners() {
        listeners.forEach {
            it.close()
        }
        listeners.forEach { l ->
            while (l.isClosedForReceive.not()) {
                val e = l.tryReceive().getOrNull()!!
                e.handled()
            }
        }
    }

    private suspend fun handleEstimateComplTsDecreased(
        newTs: Timestamp,
        fIdx: Int,
    ) {
        val oldLatest = estimateComplTs[latestTsIdx]
        val oldEarliest = estimateComplTs[earliestTsIdx]
        estimateComplTs[fIdx] = newTs

        if (latestTsIdx == fIdx) {
            latestTsIdx = estimateComplTs.withIndex().maxBy { it.value }.index
            val new = estimateComplTs[latestTsIdx]
            if (new != oldLatest) onAllComplTsDecreased(oldLatest, new)
        }

        if (earliestTsIdx == fIdx || newTs < oldEarliest) {
            earliestTsIdx = fIdx
            on1ComplTsDecreased(oldEarliest, newTs)
        }
    }

    private suspend fun handleEstimateComplTsIncreased(
        newTs: Timestamp,
        fIdx: Int,
    ) {
        val oldLatest = estimateComplTs[latestTsIdx]
        val oldEarliest = estimateComplTs[earliestTsIdx]
        estimateComplTs[fIdx] = newTs

        if (latestTsIdx == fIdx || newTs > oldLatest) {
            latestTsIdx = fIdx
            onAllComplTsIncreased(oldLatest, newTs)
        }

        if (earliestTsIdx == fIdx) {
            earliestTsIdx = estimateComplTs.withIndex().minBy { it.value}.index
            val new = estimateComplTs[earliestTsIdx]
            if (new != oldEarliest) on1ComplTsIncreased(oldEarliest, new)
        }
    }

    private suspend fun handleFragComplEstimateChanged(
        e: NetFlow.FragComplEstimateChanged,
        fIdx: Int,
    ) {
        if (e.new < e.old) {
            handleEstimateComplTsDecreased(e.new, fIdx)
        } else {
            handleEstimateComplTsIncreased(e.new, fIdx)
        }
    }

    private suspend fun handleTputChange(
        e: NetFlow.TPutChanged,
        fIdx: Int,
    ) {
        if (e.new > e.old) {
            handleEstimateComplTsDecreased(e.newComplEstimate, fIdx = fIdx)
        } else {
            handleEstimateComplTsIncreased(e.newComplEstimate, fIdx = fIdx)
        }
    }

    context(NetSimScope)
    private suspend fun handleFragCompl(
        e: NetFlow.FragCompl,
        fIdx: Int,
    ) {
        remaining -= 1
        assert(remaining >= 0)
        estimateComplTs[fIdx] = tmSrc.tmstamp

//        if (latestTsIdx == fIdx) {
//            val old = estimateComplTs[latestTsIdx]
//            latestTsIdx = estimateComplTs.withIndex().maxBy { it.value }.index
//            val new = estimateComplTs[latestTsIdx]
//            if (new != old) onAllComplTsDecreased(old, new)
//        }


        if (earliestTsIdx == fIdx && remaining > 0) {
            earliestTsIdx = estimateComplTs.withIndex().minBy { it.value}.index
            val new = estimateComplTs[earliestTsIdx]
            if (new != tmSrc.tmstamp) on1ComplTsIncreased(tmSrc.tmstamp, new)
        }

        on1FFragCompl(e.f, e.fragId)
        if (remaining == 0) onAllFFragCompl()
    }

    @OptIn(ProtectedUse::class)
    override fun close() {
        job.cancel()
    }

    public companion object {
        context(NetSimScope)
        @OptIn(ProtectedUse::class)
        @Suppress("UNCHECKED_CAST")
        public operator fun invoke(vararg flows: NetFlow): NetFTracker =
            NetFTracker(
                flows = listOf(*flows) as List<INetFlow>,
                listeners = flows.map { it.evntListener() },
            ).also {
                //
                // Start the coroutine that runs the tracker.
                val coId = NetCoId.new(NetCo.FLOW_TRACKER)
                val coName = CoroutineName("NetFlowTracker(id:${coId.value})")
                it.netRun(coId + coName)
            }
    }
}
