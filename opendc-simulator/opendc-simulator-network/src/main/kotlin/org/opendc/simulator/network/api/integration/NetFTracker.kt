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
    private val tmRm = flows.map { TimeDelta.zero }.toMutableList()
    private var maxTmRmIdx: Int = 0

    public var onTmRmIncrease: suspend (old: TimeDelta, new: TimeDelta) -> Unit = { _, _ -> }
    public var onTmRmDecrease: suspend (old: TimeDelta, new: TimeDelta) -> Unit = { _, _ -> }
    public var on1FFragCompl: suspend (f: NetFlow, fId: Any?) -> Unit = { _, _ -> }
    public var onAllFFragCompl: suspend () -> Unit = {}
    private val mtx = Mutex()
    private var fragCount: Int = 0

    @ProtectedUse override var job: Job by SetOnce()

    public suspend fun tmRm(): TimeDelta =
        mtx.withLock {
            tmRm[maxTmRmIdx]
        }

    /**
     * TODO
     * To be called after flows have been msged with the new frag msg.
     */
    public suspend fun reset(): Unit =
        mtx.withLock {
            remaining = flows.size
            maxTmRmIdx = 0
            tmRm.indices.forEach { i ->
                val f = flows[i]
                tmRm[i] = f.msgSyncReqTmRm()
                if (tmRm[i] > tmRm[maxTmRmIdx]) maxTmRmIdx = i
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

    private suspend fun handle1() {
        select {
            listeners.onEachIndexed { idx, l ->
                l.onReceive { e ->
                    try {
                        mtx.withLock {
                            when (e) {
                                is NetFlow.TPutChanged -> handleTputChange(e, idx)
                                is NetFlow.FragCompl -> handleFragCompl(e, idx)
                                is NetFlow.TmRmChanged -> handleTmRmChange(e, idx)
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

    private suspend fun handleTmRmDecreased(
        newTmRm: TimeDelta,
        fIdx: Int,
    ) {
        val old = tmRm[maxTmRmIdx]
        tmRm[fIdx] = newTmRm
        if (maxTmRmIdx == fIdx) {
            maxTmRmIdx = tmRm.withIndex().maxBy { it.value }.index
            val new = tmRm[maxTmRmIdx]
            if (new != old) onTmRmDecrease(old, tmRm[maxTmRmIdx])
        }
    }

    private suspend fun handleTmRmIncreased(
        newTmRm: TimeDelta,
        fIdx: Int,
    ) {
        val old = tmRm[maxTmRmIdx]
        tmRm[fIdx] = newTmRm
        if (maxTmRmIdx == fIdx || newTmRm > tmRm[maxTmRmIdx]) {
            maxTmRmIdx = fIdx
            onTmRmIncrease(old, tmRm[maxTmRmIdx])
        }
    }

    private suspend fun handleTmRmChange(
        e: NetFlow.TmRmChanged,
        fIdx: Int,
    ) {
        if (e.new < e.old) {
            handleTmRmDecreased(e.new, fIdx)
        } else {
            handleTmRmIncreased(e.new, fIdx)
        }
    }

    private suspend fun handleTputChange(
        e: NetFlow.TPutChanged,
        fIdx: Int,
    ) {
        if (e.new > e.old) {
            handleTmRmDecreased(e.newTmRm, fIdx = fIdx)
        } else {
            handleTmRmIncreased(e.newTmRm, fIdx = fIdx)
        }
    }

    private suspend fun handleFragCompl(
        e: NetFlow.FragCompl,
        fIdx: Int,
    ) {
        remaining -= 1
        assert(remaining >= 0)
        tmRm[fIdx] = TimeDelta.zero
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
