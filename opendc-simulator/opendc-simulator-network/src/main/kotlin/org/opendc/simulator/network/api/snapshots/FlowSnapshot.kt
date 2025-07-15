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

package org.opendc.simulator.network.api.snapshots

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.simulator.network.utils.InternalODCNApi
import org.opendc.trace.util.parquet.exporter.Exportable
import java.time.Instant

/**
 * A snapshot containing information of network collected at [instant].
 *
 * @property instant When the [FlowSnapshot] was taken.
 * @property fId The id of the flow the snapshot belongs to.
 * @property senderIp The sender node ip.
 * @property destIp The destination node ip.
 * @property demand The demand of the flow at the instant the snapshot is taken.
 * @property tput The throughput of the flow at the instant the snapshot is taken.
 */
public class FlowSnapshot private constructor(
    public val instant: Instant,
    public val fId: FlowId,
    public val senderIp: IPv4Address,
    public val destIp: IPv4Address,
    public val demand: DataRate,
    public val tput: DataRate,
) : Snapshot<FlowSnapshot>(), Exportable {
    override val dfltColWidth: Int = 27

    /**
     * @param[flags]    flags representing which property
     * need to be included in the formatted string.
     * @return the formatted string representing the snapshot information,
     * as either 1 or 2 lines with a column for each property.
     */
    override fun fmt(flags: Flags<FlowSnapshot>): String {
        val headersLine = flags.ifSet(HDR, dflt = "") { fmtHdr(flags) }

        val secondLine =
            buildString {
                append("| ")
                flags.ifSet(INSTANT) { appendPad(instant, pad = 30) }
                flags.ifSet(F_ID) { appendPad(fId) }
                flags.ifSet(SENDER_IP) { appendPad(senderIp, pad = 20) }
                flags.ifSet(DEST_IP) { appendPad(destIp, pad = 20) }
                flags.ifSet(DEMAND) { appendPad(demand) }
                flags.ifSet(TPUT) { appendPad(demand) }
            }

        return headersLine + secondLine
    }

    /**
     * @return formatted [String] line containing all the headers
     * of the fields associated with the flags [flags]. The [HDR] flags is ignored.
     */
    override fun fmtHdr(flags: Flags<FlowSnapshot>): String =
        buildString {
            append("| ")
            flags.ifSet(INSTANT) { appendPad("instant", pad = 30) }
            flags.ifSet(F_ID) { appendPad("flow_ip") }
            flags.ifSet(SENDER_IP) { appendPad("sender_ip") }
            flags.ifSet(DEST_IP) { appendPad("dest_ip") }
            flags.ifSet(DEMAND) { appendPad("demand") }
            flags.ifSet(TPUT) { appendPad("tput") }
            appendLine()
        }

    override fun toString(): String = "[NetworkSnapshot: timestamp=$instant]"

    public companion object {
        /**
         * The [Instant] the [FlowSnapshot] was taken.
         */
        public val INSTANT: Flag<FlowSnapshot> = Flag()

        /**
         * The [FlowId] of the flow the snapshot was taken of.
         */
        public val F_ID: Flag<FlowSnapshot> = Flag()

        /**
         * The sender node ip.
         */
        public val SENDER_IP: Flag<FlowSnapshot> = Flag()

        /**
         * The destination node ip.
         */
        public val DEST_IP: Flag<FlowSnapshot> = Flag()

        /**
         * The demand of the flow at the instant the snapshot is taken.
         */
        public val DEMAND: Flag<FlowSnapshot> = Flag()

        /**
         * The throughput of the flow at the instant the snapshot was taken.
         */
        public val TPUT: Flag<FlowSnapshot> = Flag()

        /**
         * Flag that adds a line to the formatted snapshot string with the fields headers.
         */
        public val HDR: Flag<FlowSnapshot> = Flag()

        /**
         * TODO
         */
        context(NetSimScope)
        @InternalODCNApi
        internal suspend fun NetFlow.snapshot(): FlowSnapshot {
            assert(this@NetFlow.id in net.flowsById)
            barrier.awaitStability()

            return barrier.whileStable {
                FlowSnapshot(
                    instant = tmSrc.instant(),
                    fId = id,
                    senderIp = srcId.toIp(),
                    destIp = destId.toIp(),
                    demand = demand,
                    tput = throughput,
                )
            }
        }
    }
}
