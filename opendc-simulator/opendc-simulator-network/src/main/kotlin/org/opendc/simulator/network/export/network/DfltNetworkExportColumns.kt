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

package org.opendc.simulator.network.export.network

import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
import org.apache.parquet.schema.Types
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.components.networks.`Network.bak`
import org.opendc.simulator.network.components.Node
import org.opendc.trace.util.parquet.exporter.ExportColumn

/**
 * This object wraps the [ExportColumn]s to solves ambiguity for field
 * names that are included in more than 1 exportable.
 *
 * Additionally, it allows to load all the columns at once by just its symbol,
 * so that these columns can be deserialized. Additional fields can be added
 * from anywhere, and they are deserializable as long as they are loaded by the jvm.
 *
 * ```kotlin
 * ...
 * // Loads the column
 * DfltNetworkExportColumns
 * ...
 * ```
 */
public object DfltNetworkExportColumns {
    /**
     * Milliseconds since EPOCH.
     */
    public val TIMESTAMP: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(INT64).named("timestamp_absolute"),
//            field =
//                Types.required(INT64)
//                    .`as`(LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MILLIS))
//                    .named("timestamp"),
        ) { it.instant.toEpochMilli() }

    /**
     * Number of [NetFlow]s currently active in the [Network].
     */
    public val NUM_FLOWS: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("flows"),
        ) { it.numActiveFlows }

    /**
     * Number of [Node]s currently active in the [Network].
     */
    public val NUM_NODES: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("nodes"),
        ) { it.numNodes }

    /**
     * Number of host nodes in the [Network].
     */
    public val NUM_HOST_NODES: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("host_nodes"),
        ) { it.numHostNodes }

    /**
     * Number of host nodes where a host is deployed in the [Network].
     */
    public val NUM_ACTIVE_HOST_NODES: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("active_host_nodes"),
        ) { it.claimedHostNodes }

    /**
     * Average throughput among all active [NetFlow]s flowing through the [Network].
     */
    public val AVRG_TPUT_PERC: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("avg_throughput_ratio"),
        ) { it.avrgTputPerc?.toRatio() }

    /**
     * The sum of throughput of all active [NetFlow] divided by the
     * sum of demand of all active [NetFlow].
     */
    public val TOT_TPUT_PERC: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("tot_tput_ratio"),
        ) { it.totTputPerc?.toRatio() }

    public val TOT_TPUT: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("tot_tput_mbps"),
        ) { it.totTput.toMbps() }

    public val WORST_TPUT_PERC: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("worst_tput_ratio"),
        ) { it.worstTputPerc?.toRatio() }

    /**
     * The sum of the current power draw` (network related) of all components in the [Network].
     */
    public val CURR_PWR_DRAW: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(DOUBLE).named("power_draw_watt"),
        ) { it.currPwrUse.toWatts() }

    /**
     * The energy consumed by the [Network] up until this moment.
     */
    public val EN_CONSUMED: ExportColumn<NetworkSnapshot> =
        ExportColumn(
            field = Types.required(DOUBLE).named("energy_consumed_joule"),
        ) { it.totEnConsumed.toJoule() }
}
