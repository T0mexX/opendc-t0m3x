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
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.utils.`observable-old`.SusChangeHndlr
import org.opendc.simulator.network.utils.`observable-old`.DelegatedObservable
import org.opendc.simulator.network.utils.`observable-old`.Observable
import org.opendc.simulator.network.utils.`observable-old`.elements.ObservableEvent
import org.opendc.simulator.network.utils.`observable-old`.elements.ObservableProperty
import org.opendc.simulator.network.utils.`observable-old`.handlers.SusEventHndlr

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
) : Observable<NetFlowBarrier> by DelegatedObservable(mode) {
    /**
     * @param mode Setting this mode to [Mode.SEQUENTIAL] or [Mode.SUSPENDING] allows to avoid some list instantiation.
     * If it is set to [Mode.BOTH], then all lists will be instantiated.
     * This parameter determines what type of handlers can be set on events.
     * @param flows The flows to be tracked by this barrier.
     */
    public constructor(mode: Mode = Mode.SUSPENDING, vararg flows: NetFlow) : this(flows.toList(), mode)

    init {
        @OptIn(InternalUse::class)
        setUpDelegatedObservable(this)
    }

    private val fragIdxs = flows.associate { f -> f.id to f.fragmentIdx }

    /**
     * Determines if throughput change handlers should be invoked.
     * If not, then also "expected time to complete fragment" handlers will not be invoked.
     */
    @Volatile
    private var tputHndlrsEnabled: Boolean = true

    private val barrierLock = Mutex()

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
     * It keeps [tmRem] up to date, as well as triggering [TIME_TO_COMPLETE] event handlers.
     */
    @OptIn(InternalUse::class)
    private val tputChangeHndlr: SusChangeHndlr<NetFlow, DataRate> =
        SusChangeHndlr { flow, old, new ->
            if (tputHndlrsEnabled.not() || old == new) return@SusChangeHndlr
            var oldTm: TimeDelta
            barrierLock.withLock {
                if (new < old) {
                    val flowTmRem: TimeDelta = flow.tmRem()
                    if (tmRemValid && flowTmRem <= tmRem) return@SusChangeHndlr
                    oldTm = tmRem
                    tmRem = flowTmRem
                } else {
                    val newTmRem: TimeDelta = flows.maxOf { it.tmRem() }
                    if (tmRemValid && newTmRem >= tmRem) return@SusChangeHndlr
                    oldTm = tmRem
                    tmRem = newTmRem
                }
                tmRemValid = true
            }
            triggerHandlers(TIME_TO_COMPLETE, oldTm, tmRem)
        }

    /**
     * Default fragment completion handler, always set on all flows.
     * Executes the barrier logic, detecting if it is completed (all flows completed their fragments),
     * invoking [BARRIER_COMPLETED] handlers and [FLOW_COMPLETED] handlers when needed.
     */
    @OptIn(InternalUse::class)
    private val fragmentCompletionHndlr: SusEventHndlr<NetFlow> =
        SusEventHndlr { flow ->
            // If the completion event received is not part of the current fragment
            // (the barrier has been reset before it was completed.)
            // TODO: remove

            barrierLock.withLock {
                if (additionalCheck && flow.fragmentIdx == fragIdxs[flow.id]) return@SusEventHndlr
                remaining -= 1
                check(remaining >= 0)
                if (remaining != 0) return@withLock
                completed = true
                timesReached++
            }
            triggerHandlers(FLOW_COMPLETED)
        }

    init {
        runBlocking {
            flows.forEach { it.withEventHndlr(NetFlow.FRAGMENT_COMPLETION, fragmentCompletionHndlr) }
            flows.forEach { it.withChangeHndlr(NetFlow.THROUGHPUT, tputChangeHndlr) }
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
            barrierLock.withLock {
                additionalCheck = completed.not()
                // If the barrier was not completed, additional checks are necessary on the next events received.
                completed = false
                remaining = flows.size
                tmRemValid = false
            }
        }

    /**
     * Enabling throughput handlers implies that "Expected Time Remaining Changed" handlers will be invoked.
     * Sequential invocation is assumed.
     */
    public fun enableThroughputHndlrs(b: Boolean): NetFlowBarrier = runBlocking {
        barrierLock.withLock {
            tputHndlrsEnabled = b
        }
        this@NetFlowBarrier
    }

    /**
     * Adds the default handler for when a flow reaches its fragment target, which sets its demand to 0.
     */
    public fun whenAFlowCompletesDemandToZero(): NetFlowBarrier = runBlocking {
        flows.forEach {
            it.withEventHndlr(NetFlow.FRAGMENT_COMPLETION) { flow -> flow.setDemand(DataRate.zero) }
        }
        this@NetFlowBarrier
    }

//    /**
//     * @return The approximate (subject to fluctuations) time that is necessary
//     * to complete the barrier (all flows complete their fragments).
//     */
//    @JvmSynthetic
//    public suspend fun approxTimeRemaining(): TimeDelta =
//        barrierLock.withLock {
//            if (tmRemValid.not()) {
//                tmRem = flows.maxOf { it.tmRem() }
//                tmRemValid = true
//            }
//            tmRem
//        }

    /**
     * TODO
     * Non-suspend, non-value-class version of [approxTimeRemaining].
     * @see approxTimeRemaining
     */
    public fun approxMsRemainingJava(): Long = runBlocking {
        approxTimeToCompletionPercentage(Percentage.ofRatio(1.0)).toMsLong()
    }

    /**
     * @return the completion [Percentage] of this fragment,
     * as the sum of data transmitted by the flows in this fragment divided by the sum of their fragment targets.
     */
    @JvmSynthetic
    public fun completionPercentage(): Percentage = flows.minOf { it.transmittedInFragment() / it.fragmentTarget }

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
        barrierLock.withLock {
            // TODO: change comments.
            // The remaining `DataSize` to be transmitted to reach a percentage of `perc` on the sum
            // of the flows' fragment targets.
            flows.maxOf {
                val toBeTransmitted: DataSize =
                    it.fragmentTarget * perc.toRatio() - it.transmittedInFragment()
                if (toBeTransmitted approxSmallerOrEq  DataSize.zero) return@maxOf TimeDelta.zero

                val currentThroughput: DataRate = it.getThroughput().ifNeg0ThenPos0()
                check(currentThroughput >= DataRate.zero) {currentThroughput}
                if (currentThroughput.isZero()) return@maxOf TimeDelta.max

                // The `DataSize` that has been transmitted in the current fragment.
                val currentlyTransmitted: DataSize = it.transmittedInFragment()

                // Expected `TimeDelta` remaining to reach `perc` (`Percentage`) completion of the sum of flows' fragments.
                (toBeTransmitted / currentThroughput).also { check(it >= TimeDelta.zero) }
            }
        }

    /**
     * Non-suspend, non-value-class version of [approxTimeToCompletionPercentage].
     * @see approxTimeToCompletionPercentage
     */
    public fun approxTimeToCompletionRatioJava(ratio: Double): Long = runBlocking {
        approxTimeToCompletionPercentage(Percentage.ofRatio(ratio)).toMsLong()
    }

    /**
     * Computes the approximate time necessary to complete the fragment for [this]~[NetFlow].
     */
    private suspend fun NetFlow.tmRem(): TimeDelta {
        val remainingToTransmit: DataSize = fragmentTarget - transmittedInFragment()
        if (remainingToTransmit <= DataSize.zero) return TimeDelta.zero

        return remainingToTransmit / getThroughput()
    }


    public companion object {
        public val FLOW_COMPLETED: ObservableEvent<NetFlowBarrier> = ObservableEvent()
        public val BARRIER_COMPLETED: ObservableProperty<NetFlowBarrier, Unit> = ObservableProperty()
        public val TIME_TO_COMPLETE: ObservableProperty<NetFlowBarrier, TimeDelta> = ObservableProperty()
    }
}
