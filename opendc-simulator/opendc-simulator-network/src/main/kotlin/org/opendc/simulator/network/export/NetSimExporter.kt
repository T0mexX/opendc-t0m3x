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

package org.opendc.simulator.network.export

import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.trace.util.parquet.exporter.Exporter
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal class NetSimExporter(
    private val exportConfig: NetworkExportConfig,
) : AbstractCoroutineContextElement(Key), AutoCloseable {
    private var lastExport: Timestamp? = null

    /**
     * TODO
     */
    private val netExporter: Exporter<NetworkSnapshot>? =
        exportConfig.networkExportColumns.takeIf { it.isNotEmpty() }?.let { cols ->
            Exporter(File(exportConfig.outputFolder.absolutePath, "network.parquet"), cols)
        }

    /**
     * TODO
     */
    private val nodeExporter: Exporter<NodeSnapshot>? =
        exportConfig.nodeExportColumns.takeIf { it.isNotEmpty() }?.let { cols ->
            Exporter(File(exportConfig.outputFolder.absolutePath, "node.parquet"), cols)
        }

    /**
     * TODO
     * TODO: make each node compute its own snapshot for performance.
     */
    context(NetSimScope)
    suspend fun exportNow(stabMode: NetSimStabilityMode = this@NetSimScope.config.stabilityMode) =
        barrier.whileStable {
            require(
                // No export interval configured.
                exportConfig.exportInterval == null ||
                    // No export yet.
                    tmSrc.tmstamp == tmSrc.initialTmStamp + exportConfig.exportInterval ||
                    // The current simulation timestamp
                    tmSrc.tmstamp approx (lastExport!! + exportConfig.exportInterval),
            )

            // Export network state.
            netExporter?.write(net.snapshot())

            // Export node states.
            net.nodesById.values.forEach { n ->
                nodeExporter?.write(n.snapshot())
            }

            lastExport = tmSrc.tmstamp
        }

    /**
     * TODO
     */
    context(NetSimScope)
    fun nextExportDeadline(): Timestamp {
        // If export config does not provide an export interval, export timestamps need to be
        requireNotNull(exportConfig.exportInterval)

        return lastExport?.let {
            it + exportConfig.exportInterval
        } ?: (tmSrc.initialTmStamp + exportConfig.exportInterval)
    }

    override fun close() {
        netExporter?.close()
        nodeExporter?.close()
    }

    companion object Key : CoroutineContext.Key<NetSimExporter>
}
