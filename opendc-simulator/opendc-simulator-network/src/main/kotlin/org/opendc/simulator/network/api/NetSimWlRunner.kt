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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.common.annotations.DebuggingUse
import org.opendc.common.logger.infoNewLine
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.common.units.Timestamp.Companion.toTimestamp
import org.opendc.common.withProgressBar
import org.opendc.common.withProgressBarSus
import org.opendc.simulator.network.api.workload.NetWorkload
import org.opendc.simulator.network.api.workload.NetworkEvent
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimTmSrc
import java.io.File
import java.io.PrintStream
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * TODO
 * runs network only simulation based on workload.
 */
public class NetSimWlRunner internal constructor(
    internal val rootScope: NetSimRootScope,
    wl: NetWorkload,
    private val tty: Boolean,
) : AutoCloseable {
    private val wl: NetWorkload

    init {
        this.wl = performVirtualMapping(wl)
    }

    /**
     * TODO
     */
    @OptIn(DebuggingUse::class, ExperimentalCoroutinesApi::class)
    public suspend fun run(): Unit =
        rootScope.launch {
            preRun()
            withProgressBarSus(max = wl.numRemainingEvents.toLong(), task = "Simulating Network...", tty = tty) pb@ {
                val simTime: TimeDelta =
                    TimeDelta.ofMillis(
                        measureTimeMillis {
                            while (wl.hasNext()) {
                                val nextDeadline = nextDeadline()

                                // Execute all network events up until `nextDeadline` timestamp.
                                this@pb.stepBy(execUntil(nextDeadline))
                                rootScope.sync()

                                // If export is needed at the reached timestamp then do.
                                exporter?.let { exp ->
                                    if (tmSrc.tmstamp == exp.nextExportDeadline()) {
                                        exp.exportNow()
                                    }
                                }
//
//                                if (nextDeadline timeDelta wl.startInstant.toTimestamp() >= TimeDelta.ofHours(48)) {
//                                    File("output/ignored/last_codump.txt").outputStream().use { out ->
//                                        PrintStream(out).use { ps ->
//                                            ps.println("nCoroutines: ${DebugProbes.dumpCoroutinesInfo().size}\n")
////                                            DebugProbes.dumpCoroutines(ps)
//                                        }
//                                    }
//                                    exitProcess(0)
//                                }
                            }
                        },
                    )

                postRun(simTime)
            }
        }.join()

    context(NetSimScope)
    private fun nextDeadline() = (exporter?.nextExportDeadline() ?: Timestamp.max) min wl.peek()!!.deadline

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
        val hToClaim = rootScope.net.getNodesById<Terminal>().keys.iterator()

        // Keeps track of the current mapping from workload ids to physical network node ids.
        val virtualMap = mutableMapOf<NodeId, NodeId>()

        // If `this` `NodeId` not already mapped then map else use mapped.
        fun NodeId.mapIfNeeded(): NodeId =
            this.takeIf {
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
            },
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
        log.infoNewLine(net.fmt())

        // Log export information
        log.infoNewLine(config.exportConfig?.fmt() ?: " ==== NO EXPORT ====")
    }

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun postRun(simTm: TimeDelta) {
        // Close exporter, necessary for file to be readable.
        exporter?.close()

        // Log total simulation time.
        log.infoNewLine("| Simulation time: $simTm")

        // Free all simulation resource and halt coroutines running the network.
        cancel()
        coroutineContext.job.join()
    }

    override fun close() {
        runBlocking {
            rootScope.cancel()
            rootScope.coroutineContext.job.join()
        }
    }
}
