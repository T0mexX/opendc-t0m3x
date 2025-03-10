package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.Percentage
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Unit.Companion.averageOfUnitOrNull
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.api.integration.HndlrsLs
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import org.opendc.simulator.network.utils.ChangeHndlr
import org.opendc.simulator.network.utils.SusChangeHndlr

public class NetFlowBarrier(private val flows: Collection<NetFlow>, mode: Mode = Mode.SUSPENDING) {

    public constructor(mode: Mode = Mode.SUSPENDING, vararg flows: NetFlow) : this(flows.toList(), mode)

    private var tputHndlrsEnabled: Boolean = true
    private val flowCompletionHndlrs = HndlrsLs<NetFlow, DataSize>(mode = mode)
    private val barrierCompletionHndlrs = HndlrsLs<NetFlowBarrier, Unit>(mode = mode)
    private val tmRemIncreasedHndlrs = HndlrsLs<NetFlowBarrier, TimeDelta>(mode = mode)
    private val tmRemDecreasedHndlrs = HndlrsLs<NetFlowBarrier, TimeDelta>(mode = mode)


    private val barrierMtx = Mutex()
    private var remaining: Int = flows.size
    public var completed: Boolean = false
        private set
    public var timesReached: Int = 0
        private set
    private var tmRem: TimeDelta = TimeDelta.max

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
                if (newTmRem < tmRem) {
                    val oldTmRem = tmRem
                    tmRem = newTmRem
                    tmRemDecreasedHndlrs.handleAll(this, oldTmRem, tmRem)
                }
            }
        }
    }

    private val fragmentCompletionHndlr: SusChangeHndlr<NetFlow, DataSize> = SusChangeHndlr { _, _, _ ->
        var last = false
        barrierMtx.withLock {
            remaining -= 1
            if (remaining == 0) {
                last = true
                remaining = flows.size
            }
        }
        if (last) {
            this.barrierCompletionHndlrs.handleAll(this, Unit, Unit)
            completed = true
            timesReached++
        }
    }

    init {
        flows.forEach { it.withFragmentCompletionHndlr(fragmentCompletionHndlr) }
//        flows.forEach { it.withThroughputChangeHndlr(tputChangeHndlr) }
    }

    public fun reset(): Unit = runBlocking {
        barrierMtx.withLock {
            completed = false
            remaining = flows.size
        }
    }

    public fun enableThroughputHndlrs(): NetFlowBarrier {
        tputHndlrsEnabled = true
        return this
    }

    public fun disableThroughputHndlrs(): NetFlowBarrier {
        tputHndlrsEnabled = false
        return this
    }

    public fun whenAFlowCompletes(f: SusChangeHndlr<NetFlow, DataSize>): NetFlowBarrier {
        this.flowCompletionHndlrs.addSus(f)
        return this
    }

    public fun whenAFlowCompletesSeq(f: ChangeHndlr<NetFlow, DataSize>): NetFlowBarrier {
        this.flowCompletionHndlrs.addSeq(f)
        return this
    }

    /**
     * Adds the default handler for when a flow reaches its target, which sets its demand to 0.
     */
    public fun whenAFlowCompletesDflt(): NetFlowBarrier {
        this.flowCompletionHndlrs.addSus {flow, _, _ -> flow.setDemand(DataRate.zero) }
        return this
    }

    public fun whenAllFlowsComplete(f: SusChangeHndlr<NetFlowBarrier, Unit>): NetFlowBarrier {
        this.barrierCompletionHndlrs.addSus(f)
        return this
    }

    public fun whenAllFlowsCompleteSeq(f: ChangeHndlr<NetFlowBarrier, Unit>): NetFlowBarrier {
        this.barrierCompletionHndlrs.addSeq(f)
        return this
    }

    public fun whenTimeRemainingIncreases(f: SusChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemIncreasedHndlrs.addSus(f)
        return this
    }

    public fun whenTimeRemainingIncreasesSeq(f: ChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemIncreasedHndlrs.addSeq(f)
        return this
    }

    public fun whenTimeRemainingDecreases(f: SusChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemDecreasedHndlrs.addSus(f)
        return this
    }

    public fun whenTimeRemainingDecreasesSeq(f: ChangeHndlr<NetFlowBarrier, TimeDelta>): NetFlowBarrier {
        tmRemDecreasedHndlrs.addSeq(f)
        return this
    }

    @JvmSynthetic
    public suspend fun approxTimeRemaining(): TimeDelta = barrierMtx.withLock {
        tmRem = flows.maxOf { it.tmRem() }
        return tmRem
    }

    public fun approxMsRemainingJava(): Long = runBlocking { approxTimeRemaining().toMsLong() }

    @JvmSynthetic
    public suspend fun completionPercentage(): Percentage =
        flows.minOf { it.transmittedInFragment() / it.fragmentTarget }

    public fun completionRatioJava(): Double = runBlocking { completionPercentage().toRatio() }

    @JvmSynthetic
    public suspend fun approxTimeToCompletionPercentage(perc: Percentage): TimeDelta = barrierMtx.withLock {
        (
            flows.sumOfUnit { it.fragmentTarget } * perc.toRatio()
                - flows.sumOfUnit { it.transmittedInFragment() }
        ) / flows.sumOfUnit { it.getThroughput() }
    }

    public fun approxTimeToCompletionRatioJava(ratio: Double): Long =
        runBlocking { approxTimeToCompletionPercentage(Percentage.ofRatio(ratio)).toMsLong() }

    private suspend fun NetFlow.tmRem(): TimeDelta = ((fragmentTarget - transmittedInFragment()) / getThroughput()).abs()
}
