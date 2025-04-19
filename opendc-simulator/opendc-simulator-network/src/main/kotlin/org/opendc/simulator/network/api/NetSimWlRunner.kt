package org.opendc.simulator.network.api

import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.common.logger.infoNewLine
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.workload.NetWorkload
import org.opendc.simulator.network.api.workload.NetworkEvent
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.export.NetSimExporter
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimTmSrc
import kotlin.system.measureTimeMillis

/**
 * TODO
 * runs network only simulation based on workload.
 */
public class NetSimWlRunner internal constructor(
    internal val netScope: NetSimScope,
    wl: NetWorkload,
): AutoCloseable {
    /**
     * TODO
     */
    private val pb = ProgressBarBuilder()
                .setInitialMax(wl.numRemainingEvents.toLong())
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Simulating network...")
                .build()

    /**
     * TODO
     */
    private val exporter: NetSimExporter? =
        netScope.config.netSimExportConfig?.let { NetSimExporter(it) }

    private val wl: NetWorkload

    init {
        this.wl = performVirtualMapping(wl)
    }

    /**
     * TODO
     */
    public suspend fun run(): Unit = with(netScope) {
        preRun()

        val simTime: TimeDelta = TimeDelta.ofMillis(
            measureTimeMillis {
                while (wl.hasNext()) {
                    val nextDeadline = nextDeadline()
//                     TODO remove
//                    check(wl.events.none { it.deadline < nextDeadline})

                    // Execute all network events up until `nextDeadline` timestamp.
                    pb.stepBy(execUntil(nextDeadline))
                    barrier.awaitStability()

                    // If export is needed at the reached timestamp then do.
                    exporter?.let { exp ->
                        if (tmSrc.tmstamp == exp.nextExportDeadline()) {
                            exp.exportNow()
                        }
                    }
                }
            }
        )

        postRun(simTime)
    }

    context(NetSimScope)
    private fun nextDeadline() =
        (exporter?.nextExportDeadline() ?: Timestamp.max) min wl.peek()!!.deadline

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun execUntil(until: Timestamp): Long {
        var processed: Long = 0
        while (wl.hasNext() && wl.peek()!!.deadline <= until) {
            wl.poll()!!.execIfNotPassed()
            processed++
        }

        (tmSrc as NetSimTmSrc.Internal).advanceBy(until timeDelta tmSrc.tmstamp)

        return processed
    }

    /**
     * TODO
     */
    private fun performVirtualMapping(wl: NetWorkload): NetWorkload {
        // TODO: try more performant

        // Available physical hosts to be mapped to workload node ids.
        val hToClaim = netScope.net.getNodesById<HostNode>().keys.iterator()

        // Keeps track of the current mapping from workload ids to physical network node ids.
        val virtualMap =  mutableMapOf<NodeId, NodeId>()

        // If `this` `NodeId` not already mapped then map else use mapped.
        fun NodeId.mapIfNeeded(): NodeId = this.takeIf {
            it == INTERNET_ID
        } ?: virtualMap[this]
            ?: kotlin.runCatching { hToClaim.next().also { virtualMap += this to it } }
            .getOrNull() ?: error("not enough hosts in topology for this workload")

        // New `NetWorkload` with all node ids corresponding to physical ids in the network.
        return NetWorkload(
            wl.events.map { evnt ->
                if (evnt !is NetworkEvent.FlowStart) return@map evnt
                evnt.copy(
                    from = evnt.from.mapIfNeeded(),
                    to = evnt.to.mapIfNeeded(),
                    // Set old
                ).also { new -> evnt.targetFlowGetter = { new.targetFlow } }
            }
        )
    }

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun preRun() {
        barrier.awaitStability()

        // Log workload information.
        log.infoNewLine(wl.fmt())

        // Log network information.
//        log.infoNewLine(net.fm) TODO
    }

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun postRun(simTm: TimeDelta) {
        // Close exporter, necessary for file to be readable.
        exporter?.close()

        // Update progress bar for the last time.
        pb.refresh()

        // Log total simulation time.
        log.infoNewLine("| Simulation time: $simTm")

        // Free all simulation resource and halt coroutines running the network.
        netScope.cancel()
        netScope.ctx.job.join()
    }

    override fun close() {
        runBlocking {
            netScope.cancel()
            netScope.ctx.job.join()
        }
    }

//
//    companion object {
//        /**
//         * TODO
//         * Afterwarsd scope is closed
//         */
//        internal suspend fun NetWorkload.runIn(scope: NetSimScope) {
//            NetSimWlRunner(scope, this).exec()
//        }
//    }
}


