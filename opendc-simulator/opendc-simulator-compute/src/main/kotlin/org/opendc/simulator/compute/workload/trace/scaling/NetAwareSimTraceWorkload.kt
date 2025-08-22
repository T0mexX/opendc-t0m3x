package org.opendc.simulator.compute.workload.trace.scaling

import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.util.BiConsumer
import org.opendc.common.logger.logger
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.common.utils.approxLargerOrEq
import org.opendc.simulator.compute.workload.trace.SimTraceWorkload
import org.opendc.simulator.compute.workload.trace.TraceWorkload
import org.opendc.simulator.engine.graph.FlowNode
import org.opendc.simulator.engine.graph.FlowSupplier
import org.opendc.simulator.engine.graph.SusUpdatableFlowNode
import org.opendc.simulator.network.api.NetIFace
import org.opendc.simulator.network.api.integration.JNetFTracker
import org.opendc.simulator.network.api.integration.NetFTracker
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.utils.SetOnce
import kotlin.math.max

/**
 * Having a specialized [SimTraceWorkload] that implements [SusUpdatableFlowNode]
 * allows to control the network simulation with the least overhead possible,
 * reducing the number of bridging calls between non-suspending and suspending contexts.
 */
public class NetAwareSimTraceWorkload(
    supplier: FlowSupplier,
    wl: TraceWorkload,
    netIFace: NetIFace,
): SimTraceWorkload(supplier, wl), SusUpdatableFlowNode {
    /*
    Nullable properties are used to be consistent with [SimTraceWorkload] (probably for garbage collector purposes?).
    Properties can easily be delegated to [org.opendc.simulator.network.utils.SetOnce]
    and initialized in the [runBlocking] block.
     */

    private var netIFace: NetIFace? = netIFace

    /**
     * Flow representing data transmitted directed outside the datacenter.
     */
    private var tx: NetFlow? = null

    /**
     * Flow representing data received by this node from outside the datacenter.
     */
    private var rx: NetFlow? = null

    /**
     * Helps to track expected fragment completion time and
     * set callbacks on events involving more than 1 flow.
     */
    private var tracker: NetFTracker? = null

    /**
     * Wraps [NetFTracker].
     *
     * Allows to set callbacks on events involving more than 1 flow that
     * are not executed immediately but sequentially on the next
     * invocation of [org.opendc.simulator.network.api.integration.JNetController.execCallbacks].
     *
     * This is needed since network simulation is running in parallel and
     * callbacks that invalidate [FlowNode] need to be executed sequentially
     * by the single thread running the compute simulation.
     */
    private var jTracker: JNetFTracker by SetOnce()

    // TODO: rm
    private var fragIdx = -1

    init {
        runBlocking {
            tx = netIFace.startFlow()
            rx = netIFace.startFlowFromInet()
            jTracker = JNetFTracker.create(netIFace.jNetIFace, tx!!, rx!!)
            tracker = jTracker.tracker

            //
            // Set up the tracker.

            // When a one fragment flow completes (either rx or tx), reset its demand to zero.
            tracker!!.on1FFragCompl = { f: NetFlow, fragId: Any? ->
                    // If when this set demand is processed, the fragment has been changed
                    // (network fragment completion and next fragment happen in the same update cycle, hence [fragId] differs),
                    // the [setDemand] has no effect.
                    f.msgAsyncSetDemand(DataRate.zero, fragId!!)
                }


            // If the estimated time remaining for the current fragment (networking) decreases,
            // invalidate this node.
            jTracker.on1ComplTsDecreased =
                BiConsumer<Long, Long> { _, _ -> invalidate() }
        }
    }

    override fun onUpdate(now: Long): Long {
        if (onUpdateWarnLogged) {
            onUpdateWarnLogged = true
            log.warn { "Executing `onUpdate` from non-suspending context. This results in reduced performance" }
        }
        return runBlocking { onUpdateSus(now) }
    }

    public override suspend fun onUpdateSus(now: Long): Long {
        val nowTs: Timestamp = Timestamp.ofEpochMs(now)
        val passedTime = this.getPassedTime(now)
        // Not really the start of fragment but the timestamp of the last update call.
        this.startOfFragment = now

        // If this.remainingWork <= 0, the fragment compute part has been completed
        // Expected completion [Timestamp] for the compute portion of the fragment.
        val compDeadline: Timestamp = onUpdateSusWaitingCompute(now, passedTime)
        assert(compDeadline >= nowTs)

        val netDeadline: Timestamp = onUpdateSusWaitingNetwork(now)
        assert(netDeadline >= nowTs)

        //
        // Note that when a network flow fragment completes, its demand is set to 0 automatically by the tracker.

        return when {
            // Both compute and network fragments are completed.
            compDeadline == nowTs && netDeadline == nowTs -> {
                startNextFragmentSus()
                invalidate()
                Long.MAX_VALUE
            }

            // Compute fragment completed but network not yet.
            compDeadline == nowTs -> {
                // Set compute demand to 0 while waiting for network.
                if (cpuFreqSupplied != .0) pushOutgoingDemand(this.machineEdge, .0)
                netDeadline.toEpochMsLong()
            }

            // Network fragment completed but compute not yet.
            netDeadline == nowTs -> compDeadline.toEpochMsLong()

            // Neither compute nor network are completed.
            else -> (compDeadline min netDeadline).toEpochMsLong()
        }
    }
    private fun onUpdateSusWaitingCompute(now: Long, passedTime: Long): Timestamp {
        assert(this.cpuFreqSupplied.approxLargerOrEq(.0, epsilon = 1e-3))
        // Sometimes it is negative and fucks__ up everything.
        this.cpuFreqSupplied = max(cpuFreqSupplied, .0)

        // The amount of work done since last update
        val finishedWork =
            scalingPolicy.getFinishedWork(this.cpuFreqDemand, this.cpuFreqSupplied, passedTime)
        this.remainingWork -= finishedWork

        this.cpuFreqSupplied = max(this.newCpuFreqSupplied, .0)

        // The amount of time required to finish the fragment at this speed
        // [max] added since [cpuFreqSupplied] can have very small negative values at times (not rounded to 0).
        // The resulting behaviour is the same, immediate rescheduling, but with lower bound we can
        // add additional assertions that would otherwise fail on these random negative values.
        val remainingDuration = max(
            scalingPolicy.getRemainingDuration(
                this.cpuFreqDemand, this.cpuFreqSupplied, this.remainingWork
            ),
            0L
        )
        assert(remainingDuration >= .0) { remainingDuration }

        if (remainingDuration.toDouble() == 0.0) {
            this.remainingWork = 0.0
        }

        return try {
            Timestamp.ofEpochMs(Math.addExact(now, remainingDuration))
        } catch (_: ArithmeticException) {
            return Timestamp.max
        }
    }

    private suspend fun onUpdateSusWaitingNetwork(now: Long): Timestamp {
        val nowTs: Timestamp = Timestamp.ofEpochMs(now)
        // Expected completion [Timestamp] for the network portion of the fragment.
        assert(
            let {
                val complNet: Timestamp = tracker!!.tsForAllCompl() ?: nowTs
                val remNet = complNet timeDelta nowTs
                remNet >= TimeDelta.zero
            }
        )

        val netDeadline: Timestamp = tracker!!.tsFor1Compl() ?: nowTs
        val remNetDeadline = netDeadline timeDelta nowTs
        assert(remNetDeadline >= TimeDelta.zero)

        return  Timestamp.ofEpochMs(
            scalingPolicy.getScaledNetworkDeadline(now, netDeadline.toEpochMsLong())
        )
    }

    /**
     * Needed for [SimTraceWorkload.makeSnapshot].
     */
    override fun startNextFragment() {
        log.warn { "Starting network fragment from non-suspending context. This results in reduced performance." }
        runBlocking { startNextFragmentSus() }
    }
    private suspend fun startNextFragmentSus() {
        fragIdx++
        super.startNextFragment()
        val frag = this.currentFragment
        frag ?: return

        //
        // Many conversion needed since both [TraceFragment]
        // and [ScalingPolicy] do not make use of units.
        val txDmnd = DataRate.ofKbps(frag.netTxKbps)
        val rxDmnd = DataRate.ofKbps(frag.netRxKbps)
        val fragDuration = TimeDelta.ofMillis(frag.duration)
        val txTarget = DataSize.ofKb((txDmnd * fragDuration).toKb())
        val rxTarget = DataSize.ofKb((rxDmnd * fragDuration).toKb())
        tx!!.msgAsyncFragInit(txTarget, frag)
        rx!!.msgAsyncFragInit(rxTarget, frag)
        tx!!.msgAsyncSetDemand(txDmnd, frag)
        rx!!.msgAsyncSetDemand(rxDmnd, frag)
        tracker!!.newFrag(frag)
    }

    override fun closeNode() {
        super.closeNode()
        if (closeWarnLogged.not()) {
            closeWarnLogged = true
            log.warn { "Closing node from non-suspending context. This results in reduced performance." }
        }
        runBlocking { closeNodeSus() }
    }
    override suspend fun closeNodeSus() {
        tracker?.close()
        netIFace?.stopFlow(tx!!)
        netIFace?.stopFlow(rx!!)

        tracker = null
        netIFace = null
        netIFace = null
        tx = null
        rx = null
    }

    private companion object {
        val log by logger()
        var closeWarnLogged = false
        var onUpdateWarnLogged = false
    }
}
