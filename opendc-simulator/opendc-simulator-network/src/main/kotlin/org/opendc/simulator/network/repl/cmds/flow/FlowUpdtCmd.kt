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
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.repl.cmds.REPLCmd

private const val CMD_STR = "updt"

internal class FlowUpdtCmd : REPLCmd(CMD_STR) {
    private val id: Long by argument(
        help = "The id of the flow whose demand is to be updated",
    ).long().check("flow does not exist") { long -> net.flowsById.contains(FlowId(long)) }

    private val newDemand: DataRate by option(
        help = "new demand",
        names = arrayOf("-b", "--bw", "--bandwidth"),
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("Unable to parse data rate '$it' (E_.g. 1Gbps)")
    }.required()

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "u" to listOf(CMD_STR),
            "update" to listOf(CMD_STR),
        )

    override fun run(): Unit =
        execREPLCmdCatching {
            barrier.awaitStability()
            val f =
                net.flowsById[FlowId(id)] ?: let {
                    echo("invalid flow id", err = true)
                    return@execREPLCmdCatching
                }
            f.setDemand(newDemand)
            barrier.awaitStability()
            echo("| Demand updated successfully, new throughput=${f.throughput}") ?: issueMessage("Unable to stop flow")
        }
}
