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

import com.github.ajalt.clikt.core.requireObject
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.CustomNetwork
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.repl.cmds.REPLCmd

private const val CMD_STR: String = "switch"

internal class NodeMkSwitchCmd : REPLCmd(name = CMD_STR) {
    private val nodeMkCtx: NodeMkCmd.NodeMkCtx by requireObject<NodeMkCmd.NodeMkCtx>()
    private val id: NodeId by lazy { nodeMkCtx.id }
    private val speed: DataRate by lazy { nodeMkCtx.portSpeed }
    private val nPorts: Int by lazy { nodeMkCtx.nPort }

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "s" to listOf(CMD_STR),
        ) + super.aliases()

    override fun run(): Unit =
        runBlocking(scope.ctx) {
            with(scope) {
                barrier.awaitStability()

                val newSwitch =
                    Switch(
                        id = id,
                        portSpeed = speed,
                        nPorts = nPorts,
                    )

                (net as? CustomNetwork)?.plus(newSwitch)
                    ?.also { barrier.awaitStability() }
                    ?.let { echo("| Added node $newSwitch") }
                    ?: issueMessage("Unable to add node.")
            }
        }
}
