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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.node.NodeId
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.connect
import org.opendc.simulator.network.repl.cmds.REPLCmd

internal class LinkMkCmd : REPLCmd("mk") {
    private val bw: DataRate by option(
        help = "The link capacity (e.g. '1 Gbps')",
        names = arrayOf("-b", "--bw", "--bandwidth"),
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("Unable to parse data rate '$it' (e.g. 1Gbps)")
    }.required().check("bandwidth must be >= 0") { it >= DataRate.ZERO }

    private val nodeIds: Set<NodeId> by option(
        help = "The id of the first node",
        names = arrayOf("-n", "--nodes", "--nodeids"),
    ).long().multiple().unique().check("nodes must be 2.") { it.size == 2 }

    override fun run(): Unit =
        runBlocking {
            net.awaitStability()
            val nodes: List<NodeId> = nodeIds.toList()
            val node1: Node? = net.nodesById[nodes[0]]
            val node2: Node? = net.nodesById[nodes[1]]

            if (node1 == null || node2 == null) {
                issueMessage("Unable to create link")
                return@runBlocking
            }

            node1.connect(node2)
            echo("Successfully connected node $node1 with  node $node2")
        }
}
