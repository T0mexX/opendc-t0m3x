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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.selects.select
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.repl.cmds.REPLCmd

internal class FlowMkCmd : REPLCmd("mk") {
    private val demand: DataRate by option(
        help = "The demand of the new flow (e.g. '1 Gbps')",
        names = arrayOf("-b", "--bw", "--bandwidth"),
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("unable to parse data rate '$it' (e.g., 1Gbps)")
    }.required().check("demand must be positive") { it >= DataRate.zero }

    private val srcIp: IPv4Address by option(
        help = "The ip of the source node",
        names = arrayOf("-s", "--senderip"),
    ).convert { str ->
        decodeOrNull<IPv4Address>(str)?.also { ip ->
            if (ip.toNId() !in net) fail("invalid src ip (not in network): $ip")
        } ?: fail("unable to parse ip/id: $str")
    }.required().check("node does not exist") { it.toNId() in net.nodesById }

    private val destIp: IPv4Address by option(
        help = "The ip of the destination node",
        names = arrayOf("-d", "--destip"),
    ).convert { str ->
        decodeOrNull<IPv4Address>(str)?.also { ip ->
            if (ip.toNId() !in net) fail("invalid dest ip (not in network): $ip")
        } ?: fail("unable to parse ip/id: $str")
    }.required().check("node does not exist") { it.toNId() in net.nodesById }

    override fun run(): Unit =
        execREPLCmdCatching {
            barrier.awaitStability()

            val newFlow =
                devConfig.netFlowConfig.version(
                    dmnd = demand,
                    srcId = srcIp.toNId(),
                    destId = destIp.toNId(),
                )

            // TODO: remove
            val listener1 = newFlow.evntListener()
            val listener2 = newFlow.evntListener()

            net.startFlow(newFlow) // TODO not remove
            var cnt = 0
            while (cnt < 2) {
                select {
                    listener1.onReceive { evnt ->
                        println(evnt)
                        evnt.handled()
                        cnt++
                    }
                    listener2.onReceive { evnt ->
                        println(evnt)
                        evnt.handled()
                        cnt++
                    }
                }
            }
            // TODO: end remove

            barrier.awaitStability()
            echo("| Started flow $newFlow")
        }
}
