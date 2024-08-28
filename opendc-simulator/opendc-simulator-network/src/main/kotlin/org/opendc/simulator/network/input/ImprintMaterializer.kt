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

package org.opendc.simulator.network.input

import org.apache.parquet.io.api.Converter
import org.apache.parquet.io.api.GroupConverter
import org.apache.parquet.io.api.PrimitiveConverter
import org.apache.parquet.io.api.RecordMaterializer
import org.apache.parquet.schema.MessageType
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID

internal class ImprintMaterializer(rqstSchema: MessageType) : RecordMaterializer<NetEventImprint>() {
    // To build network events knowledge about previous events is needed. Since there is no guarantee that
    // the parquet read is ordered by deadline, the extra step of event imprints was implemented.
    private val imprintBuilder = NetEventImprint.Builder()

    private val root: GroupConverter =
        object : GroupConverter() {
            override fun getConverter(fieldIndex: Int): Converter =
                when (rqstSchema.fields[fieldIndex]) {
                    TIMESTAMP_FIELD ->
                        object : PrimitiveConverter() {
                            override fun addLong(value: Long) {
                                imprintBuilder.deadline = Time.ofMillis(value)
                            }
                        }

                    TRANSMITTER_ID_FIELD ->
                        object : PrimitiveConverter() {
                            override fun addLong(value: Long) {
                                imprintBuilder.transmitterId = value
                            }
                        }

                    DEST_ID_FIELD ->
                        object : PrimitiveConverter() {
                            override fun addLong(value: Long) {
                                imprintBuilder.destId = value
                            }
                        }

                    NET_TX_FIELD ->
                        object : PrimitiveConverter() {
                            override fun addDouble(value: Double) {
                                imprintBuilder.netTx = DataRate.ofKbps(value)
                            }
                        }

                    FLOW_ID_FIELD ->
                        object : PrimitiveConverter() {
                            override fun addLong(value: Long) {
                                imprintBuilder.flowId = value
                            }
                        }

                    DURATION_FIELD ->
                        object : PrimitiveConverter() {
                            override fun addLong(value: Long) {
                                imprintBuilder.duration = Time.ofMillis(value)
                            }
                        }

                    else -> {
                        throw RuntimeException("unrecognized column, unable to retrieve a ${Converter::class.simpleName}")
                    }
                }

            override fun start() {
                imprintBuilder.reset()
            }

            override fun end() {
                check(imprintBuilder.transmitterId != INTERNET_ID || imprintBuilder.destId != INTERNET_ID) {
                    "either transmitter id or destination id should be defined. Null values are used to represent flow to/from internet"
                }
            }
        }

    override fun getRootConverter(): GroupConverter = root

    override fun getCurrentRecord(): NetEventImprint = imprintBuilder.build()
}
