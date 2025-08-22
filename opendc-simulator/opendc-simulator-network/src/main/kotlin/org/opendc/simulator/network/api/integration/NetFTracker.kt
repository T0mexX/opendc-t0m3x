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
import org.opendc.common.logger.logger
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.NetIFace
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NetRunnable
import org.opendc.simulator.network.components.evntemitter.Evnt
import org.opendc.simulator.network.components.evntemitter.EvntListener
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NetCoId
import org.opendc.simulator.network.utils.SetOnce

public class NetFTracker private constructor(
    private val rootScope: NetSimRootScope,
    private val flows: List<INetFlow>,
    private val listeners: List<EvntListener<NetFlow>>,
) : AutoCloseable, NetRunnable {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFTracker Properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var remaining: Int = flows.size
    private val estComplTs: MutableList<Timestamp?> =
        flows.map { null }.toMutableList<Timestamp?>().also { it.addLast(null) }

    private val nullIdx: Int = estComplTs.size - 1
    private var lastTsIdx: Int = nullIdx
    private var firstTsIdx: Int = nullIdx

    public var onAllComplTsIncreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var onAllComplTsDecreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var on1ComplTsIncreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var on1ComplTsDecreased: suspend (old: Timestamp, new: Timestamp) -> Unit = { _, _ -> }
    public var on1FFragCompl: suspend (f: NetFlow, fId: Any?) -> Unit = { _, _ -> }
    public var onAllFFragCompl: suspend () -> Unit = {}
    private val mtx = Mutex()
    private var fragId: Any = this


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetFTracker Methods
    //// Each method
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @return The estimated earliest timestamp at which a flow will complete its fragment.
     */
    public suspend fun tsFor1Compl(): Timestamp? =
        mtx.withLock {
            estComplTs[firstTsIdx]
        }.also { assert(it == null || it > rootScope.tmSrc.tmstamp) }

    /**
     * @return The estimated time remaining until a flow completes its fragment.
     */
    public suspend fun tmRmFor1Compl(): TimeDelta? =
        mtx.withLock {
            tsFor1Compl()?.timeDelta(rootScope.tmSrc.tmstamp)
        }.also { assert(it == null || it > TimeDelta.zero) }

    public suspend fun tsForAllCompl(): Timestamp? =
        mtx.withLock {
            estComplTs[lastTsIdx]
        }.also { assert(it == null || it > rootScope.tmSrc.tmstamp) }

    public suspend fun tmRmForAllCompl(): TimeDelta? =
        mtx.withLock {
            tsForAllCompl()?.timeDelta(rootScope.tmSrc.tmstamp)
        }.also { assert(it == null || it > TimeDelta.zero) }

    /**
     * To be invoked after flows have been messaged with
     * the [INetFlow.FragInit] msg through [NetFlow.msgAsyncFragInit].
     */
    context(NetSimScope)
    public suspend fun newFrag(fragId: Any): Unit = mtx.withLock {
        remaining = flows.size
        this.fragId = fragId
        lastTsIdx = nullIdx
        firstTsIdx = nullIdx
        estComplTs.indices.forEach { i ->
            if (i == nullIdx) return@forEach
            val f = flows[i]
            estComplTs[i] = f.msgSyncReqFragComplEstimate()
            val curr: Timestamp = estComplTs[i]!!
            val last = estComplTs[lastTsIdx]
            val first = estComplTs[firstTsIdx]

            // If flow has no demand.
            if (curr <= tmSrc.tmstamp) {
                remaining--
                estComplTs[i] = null
                return@forEach
            }

            // If flow will be the last to complete.
            if (last == null || curr > last) {
                lastTsIdx = i
            }

            if (first == null || estComplTs[i]!! < first) {
                firstTsIdx = i
            }
        }
//        log.debug { "New frag with flows: $flows" }
    }
    public suspend fun newFrag(fragId: Any): Unit = with(rootScope) {
        newFrag(fragId)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetRunnable
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @ProtectedUse override var job: Job by SetOnce()
    private var currEvnt: Evnt<NetFlow, *>? = null

    context(NetSimScope) @OptIn(ProtectedUse::class)
    override suspend fun netRunnableMain() {
        while (isActive) {
            handle1()
        }
    }

    context(NetSimScope) @OptIn(ProtectedUse::class)
    override suspend fun netRunnableCancellationCleanup() {
        log.debug("{} initiating closure", this)
        // If an evnt was received but not yet handled (coroutine canceled while handling it)
        // then mark it as handled.
        currEvnt?.markHandled()
        // Drain all [Evnt]s currently in the [listeners] marking them as handled.
        drainListeners()
    }

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun handle1(): Unit = select {
        listeners.onEachIndexed { idx, l ->
            l.onReceive { e ->
//                log.debug { "received event $e" }
                currEvnt = e
                mtx.withLock {
                    when (e) {
//                        is NetFlow.TPutChanged -> handleTputChange(e, idx)
                        is NetFlow.FragCompl -> handleFragCompl(e, idx)
                        is NetFlow.FragComplEstimateChanged -> handleFragComplEstimateChanged(e, idx)
                    }
                }
                e.markHandled().also { currEvnt = null }
            }
        }
    }



    context(NetSimScope)
    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun drainListeners() {
        var nDrained = 0
        listeners.forEach {
            it.close()
        }
        listeners.forEach { l ->
            while (l.isClosedForReceive.not()) {
                val e = l.tryReceive().getOrNull()!!
                e.markHandled()
                nDrained++
            }
        }
        log.debug("{} was closed with {} drained evnts", this, nDrained) // TODO: rmln
    }

    context(NetSimScope)
    private suspend fun handleEstimateComplTsDecreased(
        newTs: Timestamp,
        fIdx: Int,
    ) {
        assert(newTs > tmSrc.tmstamp)
        assert(remaining > 0)
        val oldLast = estComplTs[lastTsIdx]!!
        val oldFirst = estComplTs[firstTsIdx]!!
        estComplTs[fIdx] = newTs

        if (lastTsIdx == fIdx) {
            lastTsIdx = compLastTsIdx()
            val new = estComplTs[lastTsIdx]!!
            if (new != oldLast) onAllComplTsDecreased(oldLast, new)
        }

        if (firstTsIdx == fIdx || newTs < oldFirst) {
            firstTsIdx = fIdx
            on1ComplTsDecreased(oldFirst, newTs)
        }
    }

    context(NetSimScope)
    private suspend fun handleEstimateComplTsIncreased(
        newTs: Timestamp,
        fIdx: Int,
    ) {
        assert(newTs > tmSrc.tmstamp)
        val oldFirst = estComplTs[firstTsIdx]!!
        val oldLast = estComplTs[lastTsIdx]!!
        estComplTs[fIdx] = newTs

        if (firstTsIdx == fIdx) {
            firstTsIdx = compFirstTsIdx()
            val newFirst = estComplTs[firstTsIdx]!!
            if (newFirst != oldFirst) on1ComplTsIncreased(oldFirst, newFirst)
        }

        if (lastTsIdx == fIdx || newTs > oldLast) {
            lastTsIdx = fIdx
            onAllComplTsIncreased(oldLast, newTs)
        }
    }

    context(NetSimScope)
    private suspend fun handleFragComplEstimateChanged(
        e: NetFlow.FragComplEstimateChanged,
        fIdx: Int,
    ) {
        if (e.fragId !== this.fragId) return

        if (e.new < e.old) {
            handleEstimateComplTsDecreased(e.new, fIdx)
        } else {
            handleEstimateComplTsIncreased(e.new, fIdx)
        }
    }

//    context(NetSimScope)
//    private suspend fun handleTputChange(
//        e: NetFlow.TPutChanged,
//        fIdx: Int,
//    ) {
//        if (e.fragId !== this.fragId) return
//
//        if (e.new > e.old && e.newComplEstimate > tmSrc.tmstamp) {
//            handleEstimateComplTsDecreased(e.newComplEstimate, fIdx = fIdx)
//        } else {
//            handleEstimateComplTsIncreased(e.newComplEstimate, fIdx = fIdx)
//        }
//    }

    context(NetSimScope)
    private suspend fun handleFragCompl(
        e: NetFlow.FragCompl,
        fIdx: Int,
    ) {
        if (e.fragId !== this.fragId) return

        remaining--
        assert(remaining >= 0)
        val thisEstComplTs = estComplTs[fIdx]!!
        estComplTs[fIdx] = null

        if (firstTsIdx == fIdx && remaining > 0) {
            firstTsIdx = compFirstTsIdx()
            val new = estComplTs[firstTsIdx]
            if (new != null) on1ComplTsIncreased(tmSrc.tmstamp, new)
        }

        if (lastTsIdx == fIdx) {
            lastTsIdx = compLastTsIdx()
            val newLast = estComplTs[lastTsIdx]
            if (newLast != null && newLast != tmSrc.tmstamp) {
                assert(newLast approx thisEstComplTs)
                onAllComplTsIncreased(thisEstComplTs, newLast)
            }
        }

        on1FFragCompl(e.f, e.fragId)
        if (remaining == 0) onAllFFragCompl()
    }

    /**
     * Needs to be invoked before tracked [NetFlow]s are cancelled.
     */
    @OptIn(ProtectedUse::class)
    override fun close() {
        job.cancel()
    }

    private fun compFirstTsIdx(): Int {
        var firstIdx = nullIdx
        var curr: Timestamp? = null
        estComplTs.withIndex().forEach {
            if (it.index == nullIdx) return@forEach
            if (it.value != null && (curr == null || it.value!! < curr!!)) {
                curr = it.value
                firstIdx = it.index
            }
        }
        return firstIdx
    }

    private fun compLastTsIdx(): Int {
        var lastIdx = nullIdx
        var curr: Timestamp? = null
        estComplTs.withIndex().forEach {
            if (it.index == nullIdx) return@forEach
            if (it.value != null && (curr == null || it.value!! > curr!!)) {
                curr = it.value
                lastIdx = it.index
            }
        }
        return lastIdx
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun toString(): String = "NetFTracker($flows)"

    public companion object {
        context(NetSimScope)
        @OptIn(ProtectedUse::class)
        @Suppress("UNCHECKED_CAST")
        internal operator fun invoke(vararg flows: NetFlow): NetFTracker =
            NetFTracker(
                rootScope = this@NetSimScope.root,
                flows = listOf(*flows) as List<INetFlow>,
                listeners = flows.map { it.evntListener() },
            ).also {
                //
                // Start the coroutine that runs the tracker.
                val coId = NetCoId.new(NetCo.FLOW_TRACKER)
                val coName = CoroutineName("NetFTracker(id:${coId.value})")
                it.netRun(coId + coName)
            }

        public operator fun invoke(netIFace: NetIFace, vararg flows: NetFlow): NetFTracker = with(netIFace.scope) {
            NetFTracker(*flows)
        }

        private val log by logger()
    }

}
