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

package org.opendc.simulator.network.repl.cmds.link

import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.long
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.repl.cmds.REPLCmd

internal class LinkRmCmd : REPLCmd("rm") {
    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "remove" to listOf("rm"),
            "rem" to listOf("rm"),
        )

    private val nodeIds: Set<Long> by option(
        help = "The id of the first node",
        names = arrayOf("-N_", "--nodes", "--nodeids"),
    ).long().multiple().unique().check("nodes must be 2.") { it.size == 2 }

    override fun run(): Unit =
        execREPLCmdCatching {
            barrier.awaitStability()
            val node1: Node<*>? = net[NodeId(nodeIds.toList()[0])]
            val node2: Node<*>? = net[NodeId(nodeIds.toList()[1])]

            if (node1 == null || node2 == null) {
                issueMessage("Unable to remove link, invalid ids.")
                return@execREPLCmdCatching
            }

            node1.msgSyncDisconnect(node2)
            echo("Link removed successfully")
        }
}
