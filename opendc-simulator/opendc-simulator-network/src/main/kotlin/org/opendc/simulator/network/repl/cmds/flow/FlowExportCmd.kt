package org.opendc.simulator.network.repl.cmds.flow

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.serialization.json.Json
import org.opendc.simulator.network.api.snapshots.FlowSnapshot
import org.opendc.simulator.network.api.snapshots.FlowSnapshot.Companion.snapshot
import org.opendc.simulator.network.export.flow.DfltFlowExportColumns
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.trace.util.parquet.exporter.ExportColumn
import org.opendc.trace.util.parquet.exporter.Exporter
import java.io.File

private const val CMD_STR = "export"

internal class FlowExportCmd : REPLCmd(CMD_STR) {
    private val file: File by argument(
        help = "The id of the flow whose demand is to be updated",
    ).file()

    private val cols: List<ExportColumn<FlowSnapshot>> by option(
        names = arrayOf("-c", "--columns","--cols"),
    ).convert {
        Json.decodeFromString<ExportColumn<FlowSnapshot>>(it)
    }.multiple()

    override fun run(): Unit = execREPLCmdCatching {
        DfltFlowExportColumns
        val columns = cols.takeIf { it.isNotEmpty() } ?: ExportColumn.getAllLoadedColumns()
        val exporter = Exporter<FlowSnapshot>(file, columns)

        barrier.whileStable {
            net.flowsById.values.forEach { f ->
                exporter.write(f.snapshot())
            }
        }

        exporter.close()

        echo("Flows exported succesfully to ${file.absolutePath}")
    }
}
