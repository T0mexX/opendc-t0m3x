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

import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.repl.cmds.REPLCmd

private const val CMD_STR: String = "info"

internal class FlowInfoCmd : REPLCmd(name = CMD_STR) {
    private val nodeId: Long? by option(
        help = "Id of the node to display info of",
        names = arrayOf("-n", "--node"),
    ).long()

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "i" to listOf(CMD_STR),
        )

    override fun run(): Unit =
        runBlocking(scope.ctx) {
            with(scope) {
                scope.barrier.awaitStability()

                nodeId?.let {
                    // NodeId2 specified.
                    val node: Node? = net[NodeId(it)]
                    checkNotNull(node)
//                echo(node.fmtFlows())
                } ?: echo(net.fmtFlows())
            }
        }
}
