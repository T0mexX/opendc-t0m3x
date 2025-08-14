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

import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import inet.ipaddr.ipv4.IPv4Address
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode

private const val CMD_STR: String = "info"

internal class FlowInfoCmd : REPLCmd(name = CMD_STR) {
    private val ip: IPv4Address? by option(
        help = "Id of the node to display info of",
        names = arrayOf("-N_", "--node"),
    ).convert { str ->
        decodeOrNull<IPv4Address>(str)?.also { ip ->
            if (ip.toNId() !in net) fail("invalid node ip (not in network): $ip")
        } ?: fail("unable to parse ip/id: $str")
    }.check("node does not exist") { it.toNId() in net.nodesById }

    private val ls: Boolean by option(
        names = arrayOf("-l", "--ls"),
    ).flag(default = false)

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "i" to listOf(CMD_STR),
        )

    override fun run(): Unit =
        execREPLCmdCatching {
            barrier.awaitStability()
//            barrier.whileStable(netSimStabilityMode = NetSimStabilityMode.ENFORCED) {
                ip?.let {
                    // NodeId specified.
                    val node: Node<*>? = net[ip!!.toNId()]
                    checkNotNull(node)
//                echo(node.fmtFlows())
                } ?: echo(net.fmtFlows(ls = ls))
//            }
        }
}
