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
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.repl.cmds.REPLCmd

internal class FlowMkCmd : REPLCmd("mk") {
    private val demand: DataRate by option(
        help = "The demand of the new flow (e.g. '1 Gbps')",
        names = arrayOf("-b", "--bw", "--bandwidth"),
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("Unable to parse data rate '$it' (e.g. 1Gbps)")
    }.required().check("demand must be positive") { it >= DataRate.zero }

    private val senderId: Long by option(
        help = "The node id of the sender",
        names = arrayOf("-s", "--senderid"),
    ).long().required().check("sender invalid") { net.getNodesById<SenderNode<*>>().contains(NodeId(it)) }

    private val destId: Long by option(
        help = "The node id of the receiver",
        names = arrayOf("-d", "--destinationid", "--destid"),
    ).long().required().check("destination invalid") { net.nodesById.contains(NodeId(it)) }

    override fun run(): Unit =
        runBlocking {
            with(scope) {
                barrier.awaitStability()

                val newFlow = scope.devConfig.netFlowConfig.version(
                    demand = demand,
                    senderId = NodeId(senderId),
                    destId = NodeId(destId),
                )
                newFlow.netLaunch()

                net.startFlow(newFlow)
                barrier.awaitStability()
                echo("| Started flow $newFlow")
            }
        }
}
