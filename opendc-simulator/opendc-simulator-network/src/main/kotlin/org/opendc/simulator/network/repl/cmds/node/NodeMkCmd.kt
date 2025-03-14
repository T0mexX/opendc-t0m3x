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

package org.opendc.simulator.network.repl.cmds.node

import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.node.NodeId
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.repl.cmds.REPLCmd

internal class NodeMkCmd : REPLCmd(name = "mk") {
    private val id: NodeId by option(
        help = "The id of the new node",
        names = arrayOf("-n", "--nodeid"),
    ).long().required().check("node with id already exists") { !net.nodesById.keys.contains(it) }

    private val portSpeed: DataRate by option(
        help = "Speed of the ports on the node",
        names = arrayOf("-b", "--bw", "--bandwidth"),
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("Unable to decode data rate '$it' (e.g. 1Mbps)")
    }.required().check("port speed must be positive") { it >= DataRate.zero }

    private val nPorts: Int by option(
        help = "Number of ports on the node",
        names = arrayOf("-p", "--ports", "--nports", "--numports"),
    ).int().required()
        .check { net is CustomNetwork }

    override fun run() {
        currentContext.findOrSetObject { NodeMkCtx(id, nPorts, portSpeed) }
    }

    data class NodeMkCtx(val id: NodeId, val nPort: Int, val portSpeed: DataRate)
}
