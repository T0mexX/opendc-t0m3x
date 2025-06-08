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

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.long
import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode

private const val CMD_STR: String = "snapshot"

internal class NodeSnapCmd : REPLCmd(name = CMD_STR) {
    private val ip: IPv4Address by argument(
        help = "The id of the node whose snapshot is to be displayed",
    ).convert { str ->
        decodeOrNull<IPv4Address>(str)!!
    }.check("node does not exist") {
        println(it.toNId())
        it.toNId() in net.nodesById
    }

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "snap" to listOf(CMD_STR),
        )

    override fun run(): Unit = execREPLCmdCatching {
        barrier.whileStable(NetSimStabilityMode.ENFORCED) {
            sync(forceUpdt = true)
            echo(net.nodesById[ip.toNId()]!!.snapshot().fmt())
        }
    }
}
