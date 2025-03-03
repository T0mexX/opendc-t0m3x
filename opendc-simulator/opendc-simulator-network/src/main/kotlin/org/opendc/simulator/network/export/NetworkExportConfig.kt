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

package org.opendc.simulator.network.export

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.opendc.common.logger.logger
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.export.network.DfltNetworkExportColumns
import org.opendc.simulator.network.export.node.DfltNodeExportColumns
import org.opendc.trace.util.parquet.exporter.ColListSerializer
import org.opendc.trace.util.parquet.exporter.ExportColumn
import org.opendc.trace.util.parquet.exporter.Exportable
import org.opendc.trace.util.parquet.exporter.columnSerializer
import java.io.File

/**
 * Aggregates the necessary settings to personalize the output
 * parquet files for network simulations.
 *
 * @param[networkExportColumns]     the columns that will be included in the `network.parquet` raw output file.
 * @param[nodeExportColumns]        the columns that will be included in the `node.parquet` raw output file.
 * @param[exportInterval]           the time interval between exports.
 * If `null`, the workload events granularity will be used as the export interval.
 */
@Serializable(with = NetworkExportConfig.Companion.NetExpConfigSerializer::class)
public data class NetworkExportConfig(
    val networkExportColumns: List<ExportColumn<NetworkSnapshot>>,
    val nodeExportColumns: List<ExportColumn<NodeSnapshot>>,
    val outputFolder: File?,
    val exportInterval: Time? = null,
    val startTime: Time? = null,
) {
    /**
     * @return formatted string representing the export config.
     */
    public fun fmt(): String =
        """
        | === NETWORK EXPORT CONFIG ===
        | Network columns  : ${networkExportColumns.map { it.name }.toString().trim('[', ']')}
        | Node columns     : ${nodeExportColumns.map { it.name }.toString().trim('[', ']')}
        | Export interval  : ${exportInterval ?: "N/A"}
        | Output folder    : ${outputFolder?.absolutePath ?: "N/A"}
        """.trimIndent()

    public companion object {
        internal val LOG by logger()

        /**
         * Force the jvm to load the default [ExportColumn]s relevant to network export,
         * so that they are available for deserialization.
         */
        public fun loadDfltColumns() {
            DfltNetworkExportColumns
            DfltNodeExportColumns
        }

        /**
         * A runtime [KSerializer] is needed for reasons explained in [columnSerializer] docs.
         *
         * This serializer makes use of reified column serializers for the 2 properties.
         */
        internal object NetExpConfigSerializer : KSerializer<NetworkExportConfig> {
            private val nullableTimeSerializer: KSerializer<Time?> = kotlinx.serialization.serializer<Time?>()
            private val netColListSerializer: KSerializer<List<ExportColumn<NetworkSnapshot>>> =
                ListSerializer(columnSerializer<NetworkSnapshot>())
            private val nodeColListSerializer: KSerializer<List<ExportColumn<NodeSnapshot>>> =
                ListSerializer(columnSerializer<NodeSnapshot>())

            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("org.opendc.compute.telemetry.export.parquet.ComputeExportConfig") {
                    element(
                        "networkExportColumns",
                        netColListSerializer.descriptor,
                    )
                    element(
                        "nodeExportColumns",
                        nodeColListSerializer.descriptor,
                    )
                    element(
                        "outputFolder",
                        serialDescriptor<String>(),
                    )
                    element(
                        "exportInterval",
                        nullableTimeSerializer.descriptor,
                    )
                }

            override fun deserialize(decoder: Decoder): NetworkExportConfig {
                // Basically a recursive call with a JsonDecoder.
                val jsonDec =
                    (decoder as? JsonDecoder) ?: let {
                        return Json.decodeFromString(decoder.decodeString().trim('"'))
                    }

                // Loads the default columns so that they are available for deserialization.
                loadDfltColumns()
                val elem = jsonDec.decodeJsonElement().jsonObject

                val outputFolder: File? =
                    elem["outputFolder"]?.let { pathElem ->
                        File(pathElem.toString().trim('"')).also {
                            check(it.exists().not() || it.isDirectory)
                            it.mkdirs()
                        }
                    }

                return NetworkExportConfig(
                    networkExportColumns = elem["networkExportColumns"].toFieldList(),
                    nodeExportColumns = elem["nodeExportColumns"].toFieldList(),
                    outputFolder = outputFolder,
                    exportInterval =
                        elem["exportInterval"]?.toString()?.trim('"')?.let {
                            Json.decodeFromString(it)
                        },
                )
            }

            override fun serialize(
                encoder: Encoder,
                value: NetworkExportConfig,
            ) {
                encoder.encodeStructure(descriptor) {
                    encodeSerializableElement(descriptor, 0, netColListSerializer, value.networkExportColumns)
                    encodeSerializableElement(descriptor, 1, nodeColListSerializer, value.nodeExportColumns)
                    value.outputFolder?.let {
                        encodeStringElement(descriptor, 2, it.absolutePath.toString())
                    }
                    value.startTime?.let {
                        encodeSerializableElement(descriptor, 4, nullableTimeSerializer, value.exportInterval)
                    }
                }
            }
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private inline fun <reified T : Exportable> JsonElement?.toFieldList(): List<ExportColumn<T>> =
    this?.let {
        json.decodeFromJsonElement(ColListSerializer(columnSerializer<T>()), it)
    }?.ifEmpty {
        NetworkExportConfig.LOG.warn(
            "deserialized list of export columns for exportable ${T::class.simpleName} " +
                "produced empty list, falling back to all loaded columns",
        )
        ExportColumn.getAllLoadedColumns()
    } ?: ExportColumn.getAllLoadedColumns()
