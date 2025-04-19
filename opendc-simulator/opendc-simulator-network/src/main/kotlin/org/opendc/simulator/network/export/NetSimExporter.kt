package org.opendc.simulator.network.export

import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.export.network.DfltNetworkExportColumns
import org.opendc.simulator.network.export.node.DfltNodeExportColumns
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimTmSrc
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.trace.util.parquet.exporter.ExportColumn
import org.opendc.trace.util.parquet.exporter.Exporter
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal class NetSimExporter(
    private val exportConfig: NetworkExportConfig,
): AbstractCoroutineContextElement(Key), AutoCloseable {
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
    suspend fun exportNow(stabMode: NetSimStabilityMode = this@NetSimScope.config.stabilityMode) = barrier.whileStable {
        require(
            // No export interval configured.
            exportConfig.exportInterval == null
            // No export yet.
                || tmSrc.tmstamp == tmSrc.initialTmStamp + exportConfig.exportInterval
            // The current simulation timestamp
                || tmSrc.tmstamp approx (lastExport!! + exportConfig.exportInterval)
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
