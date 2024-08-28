/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.api.snapshots

import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.api.NetworkInterface
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.trace.util.parquet.exporter.Exportable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

public class NetIfaceSnapshot private constructor(
    public val netIface: NetworkInterface,
    public val instant: Instant,
    public val numGeneratedFlows: Int,
    public val currMinFlowTputPerc: Percentage?,
    public val currMaxFlowTputPerc: Percentage?,
    public val currAvrgFlowTputPerc: Percentage?,
    public val currTputAllFlows: DataRate,
    public val currTputPercAllFlows: Percentage?,
) : Snapshot<NetIfaceSnapshot>(), Exportable {
    override val dfltColWidth: Int = 27

    override fun fmt(flags: Flags<NetIfaceSnapshot>): String {
        val headersLine = flags.ifSet(HDR, dflt = "") { fmtHdr(flags) }

        val secondLine =
            buildString {
                append("| ")
                flags.ifSet(INSTANT) { appendPad(instant, pad = 30) }
                flags.ifSet(OWNER) { appendPad(netIface.owner) }
                flags.ifSet(NODE_ID) { appendPad(netIface.nodeId) }
                flags.ifSet(NUM_GEN_FLOWS) { appendPad(numGeneratedFlows) }
                flags.ifSet(MIN_TPUT_PERC) { appendPad(currMinFlowTputPerc?.fmtValue("%.2f")) }
                flags.ifSet(MAX_TPUT_PERC) { appendPad(currMaxFlowTputPerc?.fmtValue("%.2f")) }
                flags.ifSet(AVRG_TPUT_PERC) { appendPad(currAvrgFlowTputPerc?.fmtValue("%.2f")) }
                flags.ifSet(
                    TOT_TPUT,
                ) { appendPad("${currTputAllFlows.fmtValue("%.5f")} (${currTputPercAllFlows?.fmtValue("%.1f") ?: "N/A"})") }
            }

        return headersLine + secondLine
    }

    override fun fmtHdr(flags: Flags<NetIfaceSnapshot>): String =
        buildString {
            append("| ")
            flags.ifSet(INSTANT) { appendPad("instant", pad = 30) }
            flags.ifSet(OWNER) { appendPad("interface owner") }
            flags.ifSet(NODE_ID) { appendPad("node id") }
            flags.ifSet(NUM_GEN_FLOWS) { appendPad("generating n flows") }
            flags.ifSet(MIN_TPUT_PERC) { appendPad("curr min flow tput %") }
            flags.ifSet(MAX_TPUT_PERC) { appendPad("curr max flow tput %") }
            flags.ifSet(AVRG_TPUT_PERC) { appendPad("curr avrg flow tput %") }
            flags.ifSet(TOT_TPUT) { appendPad("curr tput (all flows) (%)") }
            appendLine()
        }

    override fun toString(): String = "[NetIfaceSnapshot: owner=${netIface.owner}, timestamp=$instant]"

    public companion object {
        public val INSTANT: Flag<NetIfaceSnapshot> = Flag()
        public val NUM_GEN_FLOWS: Flag<NetIfaceSnapshot> = Flag()
        public val MIN_TPUT_PERC: Flag<NetIfaceSnapshot> = Flag()
        public val MAX_TPUT_PERC: Flag<NetIfaceSnapshot> = Flag()
        public val AVRG_TPUT_PERC: Flag<NetIfaceSnapshot> = Flag()
        public val TOT_TPUT: Flag<NetIfaceSnapshot> = Flag()
        public val OWNER: Flag<NetIfaceSnapshot> = Flag()
        public val NODE_ID: Flag<NetIfaceSnapshot> = Flag()
        public val HDR: Flag<NetIfaceSnapshot> = Flag()
        public val ALL_NO_HDR: Flags<NetIfaceSnapshot> = Flags.all<NetIfaceSnapshot>() - HDR

        private val cache = ConcurrentHashMap<NodeId, NetIfaceSnapshot>()

        /**
         * Retrieves a snapshot of [this].
         * @param[withStableNetwork]    if `true` waits until the network is stable to take the
         * snapshot (and throws if network becomes unstable while computing it).
         * @param[noCache]              if `true` prevents the use of cache. Cache use needs to
         * be avoided when the timestamp of the snapshot is the same but events have been processed at this instant.
         */
        public fun NetworkInterface.snapshot(
            withStableNetwork: Boolean = true,
            noCache: Boolean = false,
        ): NetIfaceSnapshot {
            // If snapshot with same timestamp in cache
            if (noCache.not()) {
                cache[nodeId]?.let {
                    if (it.instant == netController.currentInstant) return it
                }
            }

            val network = netController.network
            if (withStableNetwork) {
                runBlocking {
                    network.awaitStability()
                    network.validator.checkIsStableWhile { snapshot(withStableNetwork = false) }
                }
            }

            // Flows generated by this interface sorted by throughput percentage
            val flows: List<NetFlow> = flowsById.values.sortedBy { it.throughput / it.demand }
            val activeFlows = flows.filterNot { it.demand.isZero() }

            // Sum of all flows throughput.
            val totTput: DataRate = flows.sumOfUnit { it.throughput }

            // Sum of all flows demands.
            val totDemand: DataRate = flows.sumOfUnit { it.demand }

            return NetIfaceSnapshot(
                netIface = this,
                instant = netController.currentInstant,
                numGeneratedFlows = activeFlows.size,
                currMinFlowTputPerc = activeFlows.firstOrNull()?.let { it.throughput roundedPercentageOf it.demand },
                currMaxFlowTputPerc = activeFlows.lastOrNull()?.let { it.throughput roundedPercentageOf it.demand },
                currAvrgFlowTputPerc =
                    let {
                        if (activeFlows.isEmpty()) {
                            null
                        } else {
                            activeFlows.sumOfUnit { it.throughput roundedPercentageOf it.demand } / flows.size
                        }
                    },
                currTputAllFlows = totTput,
                currTputPercAllFlows = if (activeFlows.isEmpty()) null else totTput roundedPercentageOf totDemand,
            ).also { cache[nodeId] = it }
        }
    }
}
