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

import org.opendc.common.logger.logger
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.trace.util.parquet.exporter.Exporter
import java.io.File

internal class NetExportHandler(
    private val config: NetworkExportConfig,
) : AutoCloseable {
    private var startTimestamp: Timestamp? = config.startTime
    private var nextExportDeadline: Timestamp? =
        startTimestamp?.let { startTm ->
            config.exportInterval?.let { startTm + it }
        }
        private set
    private var lastExportTimestamp: Timestamp? = null
    private val networkExporter: Exporter<NetworkSnapshot>?
    private val nodeExporter: Exporter<NodeSnapshot>?

    init {
        requireNotNull(config.outputFolder)

        with(config) {
            // NetworkExportConfig serialization guarantees that
            // outputFolder exists (if config is not null).
            val runOutputFolder =
                outputFolder.let {
                    File(it!!.absolutePath, "networking").also { f ->

                        check(f.mkdirs() || f.exists()) {
                            "unable to create directory (and parents) for path ${f.absolutePath}"
                        }
                    }
                }

            networkExporter =
                networkExportColumns.let { columns ->
                    if (columns.isEmpty()) {
                        null
                    } else {
                        Exporter(
                            outputFile = File(runOutputFolder.absolutePath, "network.parquet"),
                            columns = columns,
                        )
                    }
                }

            nodeExporter =
                nodeExportColumns.let { columns ->
                    if (columns.isEmpty()) {
                        null
                    } else {
                        Exporter(
                            outputFile = File(runOutputFolder.absolutePath, "node.parquet"),
                            columns = columns,
                        )
                    }
                }
        }
    }

    internal fun NetworkController.timeUntilExport(): TimeDelta? =
        getNextDeadline()?.timeDelta(lastUpdate).also { check(it == null || it >= TimeDelta.zero) }

    internal suspend fun NetworkController.exportIfNeeded() {
        // Non-mutable for smart cast.
        val exportDeadline = getNextDeadline()

        exportDeadline?.let {
            // If not yet time to export, then return.
            if (exportDeadline approxLarger lastUpdate) return
            // Check export deadline not passed yet.
            check(exportDeadline approx lastUpdate)
        } ?: run {
            if (lastExportTimestamp == lastUpdate) return
        }

        // Write network snapshot to the output file.
        networkExporter?.write(snapshot())

        // Write each node's snapshot to the output file.
        nodeExporter?.let {
            network.nodesById.values.forEach {
                if (it.id == INTERNET_ID) return@forEach
                nodeExporter.write(
                    it.snapshot(
                        instant = currentInstant,
//                        withStableNetwork = network,
                    ),
                )
            }
        }

        lastExportTimestamp = lastUpdate
        config.exportInterval?.let { nextExportDeadline = exportDeadline?.plus(it) }
    }

    private fun NetworkController.getNextDeadline(): Timestamp? {
        config.exportInterval ?: run { return null }
        val nextDeadline = nextExportDeadline

        return nextDeadline ?: let {
            startTimestamp = instantSrc.timestamp
            nextExportDeadline = instantSrc.timestamp + config.exportInterval

            LOG.warn(
                "start time for network export was set automatically on first " +
                    "invocation of export functions. The current time might not be the desired start time.",
            )

            nextExportDeadline!!
        }
    }

    override fun close() {
        nodeExporter?.close()
        networkExporter?.close()
    }

    private companion object {
        val LOG by logger()
    }
}
