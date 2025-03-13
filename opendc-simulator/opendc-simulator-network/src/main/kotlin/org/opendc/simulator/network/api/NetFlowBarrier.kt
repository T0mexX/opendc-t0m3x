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

package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.annotations.InternalUse
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.Percentage
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import org.opendc.simulator.network.utils.SusChangeHndlr
import org.opendc.simulator.network.utils.observable.DelegatedObservable
import org.opendc.simulator.network.utils.observable.Observable
import org.opendc.simulator.network.utils.observable.ObservableProperty

/**
 * Offers a way to observe multiple flows at the same time through the same interface,
 * being able to set handlers on events that pertain multiple flows.
 *
 * Current implementation does not scale well, it aims at observing a small set of flows.
 *
 * The barrier must be reset *before* a new fragment is started on the flows.
 *
 * @param flows The flows to be tracked by this barrier.
 * @param mode Setting this mode to [Mode.SEQUENTIAL] or [Mode.SUSPENDING] allows to avoid some list instantiation.
 * If it is set to [Mode.BOTH], then all lists will be instantiated.
 * This parameter determines what type of handlers can be set on events.
 */
public class NetFlowBarrier(
    private val flows: Collection<NetFlow>,
    mode: Mode = Mode.BOTH,
) : Observable<NetFlowBarrier> by DelegatedObservable() {
    /**
     * @param mode Setting this mode to [Mode.SEQUENTIAL] or [Mode.SUSPENDING] allows to avoid some list instantiation.
     * If it is set to [Mode.BOTH], then all lists will be instantiated.
     * This parameter determines what type of handlers can be set on events.
     * @param flows The flows to be tracked by this barrier.
     */
    public constructor(mode: Mode = Mode.SUSPENDING, vararg flows: NetFlow) : this(flows.toList(), mode)

    init {
        @OptIn(InternalUse::class)
        setUpDelegatedObserver(this)
    }

    /**
     * Determines if throughput change handlers should be invoked.
     * If not, then also "expected time to complete fragment" handlers will not be invoked.
     */
    private var tputHndlrsEnabled: Boolean = true

//    /**
//     * Invoked when a [NetFlow] assigned to this barrier completes its fragment.
//     */
//    private val flowCompletionHndlrs = HndlrsLs<NetFlow, DataSize>(mode = mode)
//
//    /**
//     * Invoked when *all* flows assigned to this barrier complete their fragment.
//     */
//    private val barrierCompletionHndlrs = HndlrsLs<NetFlowBarrier, Unit>(mode = mode)
//
//    /**
//     * Invoked when, after the throughput of a flow changed,
//     * the expected time for all flows to complete their fragments increases.
//     */
//    private val tmRemIncreasedHndlrs = HndlrsLs<NetFlowBarrier, TimeDelta>(mode = mode)
//
//    /**
//     * Invoked when, after the throughput of a flow changed,
//     * the expected time for all flows to complete their fragments decreases.
//     */
//    private val tmRemDecreasedHndlrs = HndlrsLs<NetFlowBarrier, TimeDelta>(mode = mode)

    private val barrierMtx = Mutex()

    /**
     * Number of flows that haven't reached the barrier yet (they did not complete their fragment).
     */
    private var remaining: Int = flows.size

    /**
     * `true` if the barrier is completed (all flows completed their fragment), else `false`.
     */
    public var completed: Boolean = false
        private set

    /**
     * The number of times the barrier has been completed.
     */
    public var timesReached: Int = 0
        private set

    /**
     * The current expected time to complete the barrier remaining. If throughput handlers
     * are disabled, this value might have no meaning.
     */
    private var tmRem: TimeDelta = TimeDelta.zero

    /**
     * Determines if [tmRem] has meaning, or it needs to be recomputed.
     */
    @Volatile
    private var tmRemValid = false

    /**
     * If the barrier is reset with [reset] before it was completed, then this property is set to true.
     * If this property is `true` then additional (costly) checks will be performed
     * to ensure the completion events received are part of the new fragment and not the old one.
     */
    private var additionalCheck: Boolean = false

    /**
     * This throughput handler is always set on all flows.
     * It keeps [tmRem] up to date, invoking [tmRemDecreasedHndlrs] and [tmRemIncreasedHndlrs].
     */
    @OptIn(InternalUse::class)
    private val tputChangeHndlr: SusChangeHndlr<NetFlow, DataRate> =
        SusChangeHndlr { flow, old, new ->
            if (tputHndlrsEnabled.not() || old == new) return@SusChangeHndlr

            barrierMtx.withTransferredLockHndlChange(TIME_TO_COMPLETE_CHANGE) hndlr@ {
                if (new < old) {
                    val flowTmRem: TimeDelta = flow.tmRem()
                    if (tmRemValid && flowTmRem <= tmRem) return@hndlr  null // The change does not affect barrier completion time.
                    val oldTmRem: TimeDelta = tmRem
                    tmRem = flowTmRem
                    tmRemValid = true
                    Observable.OldNewPair(oldTmRem, tmRem)
                } else {
                    val newTmRem: TimeDelta = flows.maxOf { it.tmRem() }
                    if (tmRemValid && newTmRem >= tmRem) return@hndlr null
                    val oldTmRem = tmRem
                    tmRem = newTmRem
                    tmRemValid = true
                    Observable.OldNewPair(oldTmRem, newTmRem)
                }
            }
//            if (new < old) {
//                val flowTmRem: TimeDelta = flow.tmRem()
//                barrierMtx.withLock {
//                    if (flowTmRem > tmRem) {
//                        val oldTmRem: TimeDelta = tmRem
//                        tmRem = flowTmRem
//                        handleAll(this, oldTmRem, tmRem)
//                    }
//                }
//            } else {
//                val newTmRem: TimeDelta = flows.maxOf { it.tmRem() }
//                println("bo")
//                try {
//                    barrierMtx.withLock(coroutineContext) {
//                        // If ` smaller than current max, than it might be
//                        if (newTmRem < tmRem) {
//                            val oldTmRem = tmRem
//                            tmRem = newTmRem
//                            tmRemDecreasedHndlrs.handleAll(this, oldTmRem, tmRem)
//                        }
//                    }
//                } catch (e: Exception) {
//                    throw e
//                }
//            }
        }

    /**
     * Default fragment completion handler, always set on all flows.
     * Executes the barrier logic, detecting if it is completed (all flows completed their fragments),
     * invoking [BARRIER_COMPLETED] handlers and [FLOW_COMPLETED] handlers when needed.
     */
    @OptIn(InternalUse::class)
    private val fragmentCompletionHndlr: SusChangeHndlr<NetFlow, DataSize> =
        SusChangeHndlr { flow, old, new ->
            // If the completion event received is not part of the current fragment
            // (the barrier has been reset before it was completed.)
            if (additionalCheck && flow.fragmentTarget > flow.transmittedInFragment()) return@SusChangeHndlr

            barrierMtx.withTransferredLockHndlChange(FLOW_COMPLETED) hndl@ {
                remaining -= 1
                if (remaining != 0) return@hndl null
                remaining = flows.size
                completed = true
                timesReached++
                Observable.OldNewPair(old, new)
            }
//            this.flowCompletionHndlrs.handleAll(flow, old, new)
//            if (last) {
//                this.barrierCompletionHndlrs.handleAll(this, Unit, Unit)
//                completed = true
//                timesReached++
//            }
        }

    init {
        runBlocking {
            flows.forEach { it.withHandler(NetFlow.FRAGMENT_COMPLETION, fragmentCompletionHndlr) }
            flows.forEach { it.withHandler(NetFlow.THROUGHPUT, tputChangeHndlr) }
        }
    }

    /**
     * Resets the barrier so that it can be used again (on the same set of flows).
     * If this method is invoked before the barrier is completed,
     * it is reset but late handlers may be considered new ones,
     * thus completing the barrier before they should have.
     * Barrier should be reset *before* [NetFlow]s start a new fragment.
     */
    public fun reset(): Unit =
        runBlocking {
            barrierMtx.withLock {
                // If the barrier was not completed, additional checks are necessary on the next events received.
                additionalCheck = completed.not()
                completed = false
                remaining = flows.size
            }
        }

    /**
     * Enabling throughput handlers implies that "Expected Time Remaining Changed" handlers will be invoked.
     * Sequential invocation is assumed.
     */
    public fun enableThroughputHndlrs(b: Boolean): NetFlowBarrier {
        tputHndlrsEnabled = b
        return this
    }

    /**
     * Adds the default handler for when a flow reaches its fragment target, which sets its demand to 0.
     */
    public fun whenAFlowCompletesDemandToZero(): NetFlowBarrier = runBlocking {
        flows.forEach {
            it.withHandler(NetFlow.FRAGMENT_COMPLETION) { flow, _, _ -> flow.setDemand(DataRate.zero) }
        }
        this@NetFlowBarrier
    }

    /**
     * @return The approximate (subject to fluctuations) time that is necessary
     * to complete the barrier (all flows complete their fragments).
     */
    @JvmSynthetic
    public suspend fun approxTimeRemaining(): TimeDelta =
        barrierMtx.withLock {
            if (tmRemValid.not()) {
                tmRem = flows.maxOf { it.tmRem() }
                tmRemValid = true
            }
            tmRem
        }

    /**
     * Non-suspend, non-value-class version of [approxTimeRemaining].
     * @see approxTimeRemaining
     */
    public fun approxMsRemainingJava(): Long = runBlocking { approxTimeRemaining().toMsLong() }

    /**
     * @return the completion [Percentage] of this fragment,
     * as the sum of data transmitted by the flows in this fragment divided by the sum of their fragment targets.
     */
    @JvmSynthetic
    public suspend fun completionPercentage(): Percentage = flows.minOf { it.transmittedInFragment() / it.fragmentTarget }

    /**
     * Non-suspend, non-value-class version of [completionPercentage].
     * @see completionPercentage
     */
    public fun completionRatioJava(): Double = runBlocking { completionPercentage().toRatio() }

    /**
     * @param perc The goal percentage of completion to be reached.
     * @return The approximate [TimeDelta] needed to reach that percentage.
     */
    @JvmSynthetic
    public suspend fun approxTimeToCompletionPercentage(perc: Percentage): TimeDelta =
        barrierMtx.withLock {
            (
                flows.sumOfUnit { it.fragmentTarget } * perc.toRatio() -
                    flows.sumOfUnit { it.transmittedInFragment() }
            ) / flows.sumOfUnit { it.getThroughput() }
        }

    /**
     * Non-suspend, non-value-class version of [approxTimeToCompletionPercentage].
     * @see approxTimeToCompletionPercentage
     */
    public fun approxTimeToCompletionRatioJava(ratio: Double): Long =
        runBlocking { approxTimeToCompletionPercentage(Percentage.ofRatio(ratio)).toMsLong() }

    /**
     * Computes the approximate time necessary to complete the fragment for [this]~[NetFlow].
     */
    private suspend fun NetFlow.tmRem(): TimeDelta =
        (
            (fragmentTarget - transmittedInFragment()) /
                getThroughput()
        )
            .abs()

    public companion object {
        public val FLOW_COMPLETED: ObservableProperty<NetFlowBarrier, DataSize> = ObservableProperty()
        public val BARRIER_COMPLETED: ObservableProperty<NetFlowBarrier, Unit> = ObservableProperty()
        public val TIME_TO_COMPLETE_CHANGE: ObservableProperty<NetFlowBarrier, TimeDelta> = ObservableProperty()
    }

//    /**
//     * Adds a suspend function that will be invoked when one of the flows completes its fragment.
//     */
//    public fun whenAFlowCompletes(f: SusChangeHndlr<NetFlow, DataSize>): NetFlowBarrier {
//        this.flowCompletionHndlrs.addSus(f)
//        return this
//    }
//
//    /**
//     * Adds a function will be stored when a flow completes its fragment,
//     * in the network coroutine context to be invoked sequentially in the future.
//     *
//     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
//     */
//    public fun whenAFlowCompletesSeq(f: ChangeHndlr<NetFlow, DataSize>): NetFlowBarrier {
//        this.flowCompletionHndlrs.addSeq(f)
//        return this
//    }
//
//
//    /**
//     * Adds a suspend function that will be invoked when the barrier is completed (all flows completed their fragments)
//     */
//    public fun whenAllFlowsComplete(f: SusChangeHndlr<NetFlowBarrier, Unit>): NetFlowBarrier {
//        this.barrierCompletionHndlrs.addSus(f)
//        return this
//    }
//
//    /**
//     * Adds a function that will be stored when the barrier is completed (all flows completed their fragments),
//     * in the network coroutine context to be invoked sequentially in the future.
//     *
//     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
//     */
//    public fun whenAllFlowsCompleteSeq(f: ChangeHndlr<NetFlowBarrier, Unit>): NetFlowBarrier {
//        this.barrierCompletionHndlrs.addSeq(f)
//        return this
//    }
//
//    /**
//     * Adds a suspend function that will be invoked when the expected time to complete the barrier increases.
//     */
//    public fun whenTimeRemainingIncreases(f: SusChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
//        tmRemIncreasedHndlrs.addSus(f)
//        return this
//    }
//
//    /**
//     * Adds a function that will be stored when the expected time to complete the barrier increases,
//     * in the network coroutine context to be invoked sequentially in the future.
//     *
//     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
//     */
//    public fun whenTimeRemainingIncreasesSeq(f: ChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
//        tmRemIncreasedHndlrs.addSeq(f)
//        return this
//    }
//
//    /**
//     * Adds a suspend function that will be invoked when the expected time to complete the barrier decreases.
//     */
//    public fun whenTimeRemainingDecreases(f: SusChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
//        tmRemDecreasedHndlrs.addSus(f)
//        return this
//    }
//
//    /**
//     * Adds a function that will be stored when the expected time to complete the barrier decreases,
//     * in the network coroutine context to be invoked sequentially in the future.
//     *
//     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
//     */
//    public fun whenTimeRemainingDecreasesSeq(f: ChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
//        tmRemDecreasedHndlrs.addSeq(f)
//        return this
//    }
}
