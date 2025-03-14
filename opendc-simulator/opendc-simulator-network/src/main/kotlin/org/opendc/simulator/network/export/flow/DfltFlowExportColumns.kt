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

package org.opendc.simulator.network.export.flow

import org.apache.parquet.io.api.Binary
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
import org.apache.parquet.schema.Types
import org.opendc.simulator.network.api.snapshots.FlowSnapshot
import org.opendc.trace.util.parquet.exporter.ExportColumn

public object DfltFlowExportColumns {
    /**
     * Milliseconds since EPOCH.
     */
    public val TIMESTAMP: ExportColumn<FlowSnapshot> =
        ExportColumn(
            field =
                Types.required(INT64)
                    .`as`(LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MILLIS))
                    .named("timestamp"),
        ) { it.instant.toEpochMilli() }

    /**
     * The id of the flow the snapshot belongs to.
     */
    public val F_ID: ExportColumn<FlowSnapshot> =
        ExportColumn(
            field = Types.required(INT64).named("id"),
        ) { it.fId.value }

    /**
     * The sender node ip.
     */
    public val SENDER_IP: ExportColumn<FlowSnapshot> =
        ExportColumn(
            field = Types.required(BINARY).named("sender_ip"),
        ) { Binary.fromString(it.senderIp.toString()) }

    /**
     * The destination node ip.
     */
    public val DEST_IP: ExportColumn<FlowSnapshot> =
        ExportColumn(
            field = Types.required(BINARY).named("dest_ip"),
        ) { Binary.fromString(it.destIp.toString()) }

    /**
     * The demand of the flow.
     */
    public val DEMAND: ExportColumn<FlowSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("demand_Kbps"),
        ) { it.demand.toKbps() }

    /**
     * The throughput of the flow.
     */
    public val TPUT: ExportColumn<FlowSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("tput_Kbps"),
        ) { it.tput.toKbps() }
}
