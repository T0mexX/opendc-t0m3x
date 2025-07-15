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

package org.opendc.simulator.network.repl.cmds.flow

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.serialization.json.Json
import org.opendc.simulator.network.api.snapshots.FlowSnapshot
import org.opendc.simulator.network.api.snapshots.FlowSnapshot.Companion.snapshot
import org.opendc.simulator.network.export.flow.DfltFlowExportColumns
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.utils.InternalODCNApi
import org.opendc.trace.util.parquet.exporter.ExportColumn
import org.opendc.trace.util.parquet.exporter.Exporter
import org.opendc.trace.util.parquet.exporter.columnSerializer
import java.io.File

private const val CMD_STR = "export"

internal class FlowExportCmd : REPLCmd(CMD_STR) {
    private val file: File by argument(
        help = "The id of the flow whose demand is to be updated",
    ).file()

    private val cols: List<ExportColumn<FlowSnapshot>>? by option(
        help = "export columns (e.g., '--columns [<col1>,<col2>]')",
        names = arrayOf("-c", "--columns", "--cols"),
    ).convert { str ->
        DfltFlowExportColumns
        str.trim('[', ']', '"')
            .split(",")
            .map {
                Json.decodeFromString(columnSerializer(), "\"$it\"")
            }
    }

    @OptIn(InternalODCNApi::class)
    override fun run(): Unit =
        execREPLCmdCatching {
            DfltFlowExportColumns

            val exporter = Exporter<FlowSnapshot>(file, cols ?: ExportColumn.getAllLoadedColumns())

            barrier.whileStable {
                net.flowsById.values.forEach { f ->
                    exporter.write(f.snapshot())
                }
            }

            exporter.close()

            echo("Flows exported succesfully to ${file.absolutePath}")
        }
}
