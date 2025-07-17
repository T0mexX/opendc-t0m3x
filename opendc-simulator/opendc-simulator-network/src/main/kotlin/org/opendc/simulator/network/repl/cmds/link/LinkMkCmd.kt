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
import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.repl.cmds.REPLCmd

private const val CMD_STR: String = "mk"

internal class LinkMkCmd : REPLCmd(CMD_STR) {
    private val bw: DataRate? by option(
        help = "The link capacity (e.g., '1 Gbps')",
        names = arrayOf("-b", "--bw", "--bandwidth"),
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("unable to parse data rate '$it' (e.g., 1Gbps)")
    }.check("bandwidth must be >= 0") { it >= DataRate.zero }

    private val nodeIps: Set<IPv4Address> by option(
        help = "The ips or ids of the nodes",
        names = arrayOf("-n", "--node", ),
    ).convert { str ->
        decodeOrNull<IPv4Address>(str)?.also { ip ->
            if (ip.toNId() !in net) fail("invalid ip (not in network): $ip")
        } ?: fail("unable to parse ip/id: $str")
    }.multiple().unique().check("nodes must be 2.") { it.size == 2 }

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "mk" to listOf(CMD_STR),
        ) + super.aliases()

    override fun run(): Unit =
        execREPLCmdCatching {
            barrier.awaitStability()
            val node1: Node<*> = net[nodeIps.toList()[0]]!!
            val node2: Node<*> = net[nodeIps.toList()[1]]!!

            if (bw != null) node1.msgSyncConnect(node2, linkBw = bw!!)
            else node1.msgSyncConnect(node2)

            barrier.awaitStability()
            echo("| Connected nodes $node1, $node2")
        }
}
