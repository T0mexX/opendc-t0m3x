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

import org.opendc.common.units.DataRate
import org.opendc.common.units.Energy
import org.opendc.common.units.Percentage
import org.opendc.common.units.Power
import org.opendc.common.units.Unit.Companion.averageOfUnitOrNull
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.api.NetEnRecorder
import org.opendc.simulator.network.flow.neww.publics.NetFlow
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.HDR
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.HDR
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.trace.util.parquet.exporter.Exportable
import java.time.Instant

/**
 * A snapshot containing information of network collected at [instant].
 *
 * @property[instant]                   The [Instant] the [NodeSnapshot] was taken.
 * @property[numNodes]                  The number of nodesById in the network at the instant the snapshot is taken.
 * @property[numHostNodes]              The number of host nodesById in the network at the instant the snapshot is taken.
 * @property[claimedHostNodes]          The number of host nodesById whose interface was claimed at the instant the snapshot was taken.
 * @property[numCoreSwitches]           The number of core switches in the network at the instant the snapshot is taken.
 * @property[numActiveFlows]            The number of flows transiting through the network at the instant the snapshot is taken.
 * @property[totTput]                   The total throughput of the flows, as the sum of their throughput.
 * @property[totTputPerc]               The total throughput percentage of the flows, as the sum of their throughput divided by the sum of their demand.
 * @property[avrgTputPerc]              The average throughput among all flows, as the sum of the throughput percentage of each flow, divided by the number of flows.
 * @property[currPwrUse]                The power usage at the instant the snapshot was taken.
 * @property[avrgPwrUseOverTime]        The average power usage of the network, from the instant the node was started until the instant the snapshot was taken.
 * @property[totEnConsumed]             The energy consumed from the instant the network was started until the instant the snapshot was taken.
 *
 */
public class NetworkSnapshot private constructor(
    public val instant: Instant,
    public val numNodes: Int,
    public val numHostNodes: Int,
    public val claimedHostNodes: Int,
    public val numCoreSwitches: Int,
    public val numActiveFlows: Int,
    public val totTput: DataRate,
    public val totTputPerc: Percentage?,
    public val avrgTputPerc: Percentage?,
    public val worstTputPerc: Percentage?,
    public val currPwrUse: Power,
    public val avrgPwrUseOverTime: Power,
    public val totEnConsumed: Energy,
) : Snapshot<NetworkSnapshot>(), Exportable {
    override val dfltColWidth: Int = 27

    /**
     * @param[flags]    flags representing which property
     * need to be included in the formatted string.
     * @return the formatted string representing the snapshot information,
     * as either 1 or 2 lines with a column for each property.
     */
    override fun fmt(flags: Flags<NetworkSnapshot>): String {
        val headersLine = flags.ifSet(HDR, dflt = "") { fmtHdr(flags) }

        val secondLine =
            buildString {
                append("| ")
                flags.ifSet(INSTANT) { appendPad(instant, pad = 30) }
                flags.ifSet(NODES) { appendPad(numNodes) }
                flags.ifSet(HOST_NODES) { appendPad("$numHostNodes($claimedHostNodes)") }
                flags.ifSet(CORE_SWITCHES) { appendPad(numCoreSwitches) }
                flags.ifSet(FLOWS) { appendPad(numActiveFlows) }
                flags.ifSet(TOT_TPUT) { appendPad(totTput.fmtValue("%.5f")) }
                flags.ifSet(TOT_TPUT_PERC) { appendPad(totTputPerc?.fmtValue("%.5f")) }
                flags.ifSet(AVRG_TPUT_PERC) { appendPad(avrgTputPerc?.fmtValue("%.5f")) }
                flags.ifSet(CURR_PWR_USE) { appendPad(currPwrUse.fmtValue("%.5f")) }
                flags.ifSet(AVRG_PWR_USE) { appendPad(avrgPwrUseOverTime.fmtValue("%.5f")) }
                flags.ifSet(EN_CONSUMED) { appendPad(totEnConsumed.fmtValue("%.5f")) }
            }

        return headersLine + secondLine
    }

    /**
     * @return formatted [String] line containing all the headers
     * of the fields associated with the flags [flags]. The [HDR] flags is ignored.
     */
    override fun fmtHdr(flags: Flags<NetworkSnapshot>): String =
        buildString {
            append("| ")
            flags.ifSet(INSTANT) { appendPad("instant", pad = 30) }
            flags.ifSet(NODES) { appendPad("nodes") }
            flags.ifSet(HOST_NODES) { appendPad("hosts (assigned)") }
            flags.ifSet(CORE_SWITCHES) { appendPad("core switches") }
            flags.ifSet(FLOWS) { appendPad("active flows") }
            flags.ifSet(TOT_TPUT) { appendPad("tot throughput") }
            flags.ifSet(TOT_TPUT_PERC) { appendPad("tot throughput %") }
            flags.ifSet(AVRG_TPUT_PERC) { appendPad("avrg throughput %") }
            flags.ifSet(CURR_PWR_USE) { appendPad("curr pwr use") }
            flags.ifSet(AVRG_PWR_USE) { appendPad("avrg pwr over time") }
            flags.ifSet(EN_CONSUMED) { appendPad("energy consumed") }
            appendLine()
        }

    override fun toString(): String = "[NetworkSnapshot: timestamp=$instant]"

    public companion object {
        /**
         * The [Instant] the [NodeSnapshot] was taken.
         */
        public val INSTANT: Flag<NetworkSnapshot> = Flag()

        /**
         * The number of nodesById in the network at the instant the snapshot was taken.
         */
        public val NODES: Flag<NetworkSnapshot> = Flag()

        /**
         * The number of host nodesById in the network at the instant the snapshot was taken.
         */
        public val HOST_NODES: Flag<NetworkSnapshot> = Flag()

        /**
         * The number of core switches in the network at the instant the snapshot was taken.
         */
        public val CORE_SWITCHES: Flag<NetworkSnapshot> = Flag()

        /**
         * The number of flows transiting through the network at the instant the snapshot is taken.
         */
        public val FLOWS: Flag<NetworkSnapshot> = Flag()

        /**
         * The total data-rate transiting through the network at the instant the snapshot was taken.
         */
        public val TOT_TPUT: Flag<NetworkSnapshot> = Flag()

        /**
         * The total throughput percentage of all the flows transiting through
         * the network as the sum of their throughput divided by the sum of their demand.
         */
        public val TOT_TPUT_PERC: Flag<NetworkSnapshot> = Flag()

        /**NodeSna
         * The average throughput percentage of all the flows transiting through
         * the network at the instant the snapshot was taken.
         */
        public val AVRG_TPUT_PERC: Flag<NetworkSnapshot> = Flag()

        /**
         * The power usage at the instant the snapshot was taken.
         */
        public val CURR_PWR_USE: Flag<NetworkSnapshot> = Flag()

        /**
         * The average power usage of the network, from the instant the node
         * was started until the instant the snapshot was taken.
         */
        public val AVRG_PWR_USE: Flag<NetworkSnapshot> = Flag()

        /**
         * The energy consumed from the instant the network was
         * started until the instant the snapshot was taken.
         */
        public val EN_CONSUMED: Flag<NetworkSnapshot> = Flag()

        /**
         * Flag that adds a line to the formatted snapshot string with the fields headers.
         */
        public val HDR: Flag<NetworkSnapshot> = Flag()

        /**
         * "Flag" that includes all the other flags except [HDR].
         */
        public val ALL_NO_HDR: Flags<NetworkSnapshot> = Flags.all<NetworkSnapshot>() - HDR

        private var lastSnapshot: NetworkSnapshot? = null

        /**
         * Retrieves a snapshot of [this].
         * @param[noCache] if `true` prevents the use of cache. Cache use needs to
         * be avoided when the timestamp of the snapshot is the same but events have been processed at this instant.
         */
        public suspend fun NetworkController.snapshot(noCache: Boolean = false): NetworkSnapshot =
            network.snapshot(noCache, instantSrc.instant(), energyRecorder)

        /**
         * Retrieves a snapshot of [this].
         * @param[noCache] if `true` prevents the use of cache. Cache use needs to
         * be avoided when the timestamp of the snapshot is the same but events have been processed at this instant.
         */
        public suspend fun Network.snapshot(
            noCache: Boolean = false,
            instant: Instant,
            enRecorder: NetEnRecorder,
        ): NetworkSnapshot {
            if (noCache.not()) {
                lastSnapshot?.let {
                    if (it.instant == instant) return it
                }
            }

            val flows: Collection<NetFlow> = this.flowsById.values
            val activeFlows: Collection<NetFlow> = flows.filterNot { it.getDemand().isZero() }
            val totDemand: DataRate = flows.sumOfUnit { it.getDemand() }
            val totThroughput: DataRate = flows.sumOfUnit { it.getThroughput() }

            this@snapshot.awaitStability()

            return NetworkSnapshot(
                instant = instant,
                numNodes = this.nodesById.size,
                numHostNodes = this.getNodesById<HostNode>().size,
                claimedHostNodes = this.getNodesById<HostNode>().size,
                numCoreSwitches = this.getNodesById<CoreSwitch>().size,
                numActiveFlows = activeFlows.size,
                totTput = totThroughput,
                totTputPerc = if (activeFlows.isEmpty()) null else totThroughput roundedPercentageOf totDemand,
                avrgTputPerc = activeFlows.averageOfUnitOrNull { it.getThroughput() roundedPercentageOf it.getDemand() },
                worstTputPerc = activeFlows.minOfOrNull { it.getThroughput() roundedPercentageOf it.getDemand() },
                currPwrUse = enRecorder.currPwrUsage,
                avrgPwrUseOverTime = enRecorder.avrgPwrUsage,
                totEnConsumed = enRecorder.totalConsumption,
            ).also { lastSnapshot = it }
        }
    }
}
