package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.Percentage
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.utils.HndlrsLs
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import org.opendc.simulator.network.utils.ChangeHndlr
import org.opendc.simulator.network.utils.SusChangeHndlr


/**
 * Offers a way to observe multiple flows at the same time through the same interface,
 * being able to set handlers on events that pertain multiple flows.
 *
 * Current implementation does not scale well, it aims at observing a small set of flows.
 *
 * @param flows The flows to be tracked by this barrier.
 * @param mode Setting this mode to [Mode.SEQUENTIAL] or [Mode.SUSPENDING] allows to avoid some list instantiation.
 * If it is set to [Mode.BOTH], then all lists will be instantiated.
 * This parameter determines what type of handlers can be set on events.
 */
public class NetFlowBarrier(private val flows: Collection<NetFlow>, mode: Mode = Mode.BOTH) {

    /**
     * @param mode Setting this mode to [Mode.SEQUENTIAL] or [Mode.SUSPENDING] allows to avoid some list instantiation.
     * If it is set to [Mode.BOTH], then all lists will be instantiated.
     * This parameter determines what type of handlers can be set on events.
     * @param flows The flows to be tracked by this barrier.
     */
    public constructor(mode: Mode = Mode.SUSPENDING, vararg flows: NetFlow) : this(flows.toList(), mode)

    /**
     * Determines if throughput change handlers should be invoked.
     * If not, then also "expected time to complete fragment" handlers will not be invoked.
     */
    private var tputHndlrsEnabled: Boolean = true

    /**
     * Invoked when a [NetFlow] assigned to this barrier completes its fragment.
     */
    private val flowCompletionHndlrs = HndlrsLs<NetFlow, DataSize>(mode = mode)

    /**
     * Invoked when *all* flows assigned to this barrier complete their fragment.
     */
    private val barrierCompletionHndlrs = HndlrsLs<NetFlowBarrier, Unit>(mode = mode)

    /**
     * Invoked when, after the throughput of a flow changed,
     * the expected time for all flows to complete their fragments increases.
     */
    private val tmRemIncreasedHndlrs = HndlrsLs<NetFlowBarrier, TimeDelta>(mode = mode)

    /**
     * Invoked when, after the throughput of a flow changed,
     * the expected time for all flows to complete their fragments decreases.
     */
    private val tmRemDecreasedHndlrs = HndlrsLs<NetFlowBarrier, TimeDelta>(mode = mode)

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
    private var tmRem: TimeDelta = TimeDelta.max

    /**
     * Determines if [tmRem] has meaning, or it needs to be recomputed.
     */
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
    private val tputChangeHndlr: SusChangeHndlr<NetFlow, DataRate> = SusChangeHndlr { flow, old, new ->
        if (tputHndlrsEnabled.not()) return@SusChangeHndlr

        if (new < old) {
            val flowTmRem: TimeDelta = flow.tmRem()
            barrierMtx.withLock {
                if (flowTmRem > tmRem) {
                    val oldTmRem: TimeDelta = tmRem
                    tmRem = flowTmRem
                    tmRemIncreasedHndlrs.handleAll(this, oldTmRem, tmRem)
                }
            }
        } else {
            val newTmRem: TimeDelta = flows.maxOf { it.tmRem() }
            barrierMtx.withLock {
                // If ` smaller than current max, than it might be
                if (newTmRem < tmRem) {
                    val oldTmRem = tmRem
                    tmRem = newTmRem
                    tmRemDecreasedHndlrs.handleAll(this, oldTmRem, tmRem)
                }
            }
        }
    }

    /**
     * Default fragment completion handler, always set on all flows.
     * Executes the barrier logic, detecting if it is completed (all flows completed their fragments),
     * invoking [barrierCompletionHndlrs] and [flowCompletionHndlrs] wehn needed.
     */
    private val fragmentCompletionHndlr: SusChangeHndlr<NetFlow, DataSize> = SusChangeHndlr { flow, old, new ->
        // If the completion event received is not part of the current fragment
        // (the barrier has been reset before it was completed.)
        if (additionalCheck && flow.fragmentTarget > flow.transmittedInFragment()) return@SusChangeHndlr

        var last = false
        barrierMtx.withLock {
            remaining -= 1
            if (remaining == 0) {
                last = true
                remaining = flows.size
            }
        }
        this.flowCompletionHndlrs.handleAll(flow, old, new)
        if (last) {
            this.barrierCompletionHndlrs.handleAll(this, Unit, Unit)
            completed = true
            timesReached++
        }
    }

    init {
        flows.forEach { it.withFragmentCompletionHndlr(fragmentCompletionHndlr) }
        flows.forEach { it.withThroughputChangeHndlr(tputChangeHndlr) }
    }

    /**
     * Resets the barrier so that it can be used again (on the same set of flows).
     * If this method is invoked before the barrier is completed,
     * it is reset but late handlers may be considered new ones,
     * thus completing the barrier before they should have.
     * Barrier should be reset *before* [NetFlow]s start a new fragment.
     */
    public fun reset(): Unit = runBlocking {
        barrierMtx.withLock {
            // If the barrier was not completed, additional checks are necessary on the next events received.
            additionalCheck = completed.not()
            completed = false
            remaining = flows.size
        }
    }

    /**
     * Enabling throughput handlers implies that "Expected Time Remaining Changed" handlers will be invoked.
     */
    public fun enableThroughputHndlrs(): NetFlowBarrier {
        tputHndlrsEnabled = true
        return this
    }

    /**
     * Disabling throughput handlers implies that "Expected Time Remaining Changed" handlers will not be invoked.
     */
    public fun disableThroughputHndlrs(): NetFlowBarrier {
        tputHndlrsEnabled = false
        return this
    }

    /**
     * Adds a suspend function that will be invoked when one of the flows completes its fragment.
     */
    public fun whenAFlowCompletes(f: SusChangeHndlr<NetFlow, DataSize>): NetFlowBarrier {
        this.flowCompletionHndlrs.addSus(f)
        return this
    }

    /**
     * Adds a function will be stored when a flow completes its fragment,
     * in the network coroutine context to be invoked sequentially in the future.
     *
     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
     */
    public fun whenAFlowCompletesSeq(f: ChangeHndlr<NetFlow, DataSize>): NetFlowBarrier {
        this.flowCompletionHndlrs.addSeq(f)
        return this
    }

    /**
     * Adds the default handler for when a flow reaches its fragment target, which sets its demand to 0.
     */
    public fun whenAFlowCompletesDflt(): NetFlowBarrier {
        this.flowCompletionHndlrs.addSus {flow, _, _ -> flow.setDemand(DataRate.zero) }
        return this
    }

    /**
     * Adds a suspend function that will be invoked when the barrier is completed (all flows completed their fragments)
     */
    public fun whenAllFlowsComplete(f: SusChangeHndlr<NetFlowBarrier, Unit>): NetFlowBarrier {
        this.barrierCompletionHndlrs.addSus(f)
        return this
    }

    /**
     * Adds a function that will be stored when the barrier is completed (all flows completed their fragments),
     * in the network coroutine context to be invoked sequentially in the future.
     *
     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
     */
    public fun whenAllFlowsCompleteSeq(f: ChangeHndlr<NetFlowBarrier, Unit>): NetFlowBarrier {
        this.barrierCompletionHndlrs.addSeq(f)
        return this
    }

    /**
     * Adds a suspend function that will be invoked when the expected time to complete the barrier increases.
     */
    public fun whenTimeRemainingIncreases(f: SusChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemIncreasedHndlrs.addSus(f)
        return this
    }

    /**
     * Adds a function that will be stored when the expected time to complete the barrier increases,
     * in the network coroutine context to be invoked sequentially in the future.
     *
     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
     */
    public fun whenTimeRemainingIncreasesSeq(f: ChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemIncreasedHndlrs.addSeq(f)
        return this
    }

    /**
     * Adds a suspend function that will be invoked when the expected time to complete the barrier decreases.
     */
    public fun whenTimeRemainingDecreases(f: SusChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemDecreasedHndlrs.addSus(f)
        return this
    }

    /**
     * Adds a function that will be stored when the expected time to complete the barrier decreases,
     * in the network coroutine context to be invoked sequentially in the future.
     *
     * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
     */
    public fun whenTimeRemainingDecreasesSeq(f: ChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemDecreasedHndlrs.addSeq(f)
        return this
    }

    /**
     * @return The approximate (subject to fluctuations) time that is necessary
     * to complete the barrier (all flows complete their fragments).
     */
    @JvmSynthetic
    public suspend fun approxTimeRemaining(): TimeDelta = barrierMtx.withLock {
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
    public suspend fun completionPercentage(): Percentage =
        flows.minOf { it.transmittedInFragment() / it.fragmentTarget }

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
    public suspend fun approxTimeToCompletionPercentage(perc: Percentage): TimeDelta = barrierMtx.withLock {
        (
            flows.sumOfUnit { it.fragmentTarget } * perc.toRatio()
                - flows.sumOfUnit { it.transmittedInFragment() }
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
    private suspend fun NetFlow.tmRem(): TimeDelta = ((fragmentTarget - transmittedInFragment()) / getThroughput()).abs()
}
