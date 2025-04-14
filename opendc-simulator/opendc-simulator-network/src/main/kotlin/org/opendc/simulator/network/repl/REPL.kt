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

package org.opendc.simulator.network.repl

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.components.networks.CustomNetwork
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.repl.cmds.AdvTimeCmd
import org.opendc.simulator.network.repl.cmds.EnRepCmd
import org.opendc.simulator.network.repl.cmds.ExportCmd
import org.opendc.simulator.network.repl.cmds.ImportCmd
import org.opendc.simulator.network.repl.cmds.QuitCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowInfoCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowMkCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowRmCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowUpdtCmd
import org.opendc.simulator.network.repl.cmds.link.LinkCmd
import org.opendc.simulator.network.repl.cmds.link.LinkMkCmd
import org.opendc.simulator.network.repl.cmds.link.LinkRmCmd
import org.opendc.simulator.network.repl.cmds.network.NetCmd
import org.opendc.simulator.network.repl.cmds.network.NetSnapCmd
import org.opendc.simulator.network.repl.cmds.node.NodeCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkCoreSwitchCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkHostCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkSwitchCmd
import org.opendc.simulator.network.repl.cmds.node.NodeRmCmd
import org.opendc.simulator.network.repl.cmds.node.NodeSnapCmd
import org.opendc.simulator.network.simscope.NetSimScope
import java.time.Instant

public suspend fun main() {
    val scope = NetSimScope()
    val network: Network
    val env: REPLEnv
    with(scope) {
        network = CustomNetwork()
//            val energyRecorder = NetEnRecorder(network)
    }
    env =
        REPLEnv(
            network = network,
            scope = scope,
//                    energyRecorder = energyRecorder,
//            tmSrc = REPLTmSrc(Instant.now()),
        )

    while (true) {
        val input: String = readln()
        val inputArr: List<String> = input.trim().split("\\s+".toRegex())
        val cmd: CliktCommand =
            MainCmd(env).subcommands(
                LinkCmd().subcommands(LinkMkCmd(), LinkRmCmd()),
                NodeCmd().subcommands(
                    NodeRmCmd(),
                    NodeMkCmd().subcommands(NodeMkHostCmd(), NodeMkSwitchCmd(), NodeMkCoreSwitchCmd()),
                    NodeSnapCmd(),
                ),
                AdvTimeCmd(),
                FlowCmd().subcommands(

                    FlowMkCmd(),
                    FlowInfoCmd(),
                    FlowRmCmd(),
                    FlowUpdtCmd(),
                ),
                EnRepCmd(),
                ExportCmd(),
                NetCmd().subcommands(
                    NetSnapCmd(),
                ),
                ImportCmd(),
                QuitCmd(),
            )
        try {
            cmd.parse(inputArr)
        } catch (e: PrintHelpMessage) {
            println(e.command.getFormattedHelp())
        } catch (e: CliktError) {
            e.message?.let { println(it) } ?: println("vianofgao")
        }
    }
}

private class MainCmd(private val env: REPLEnv) : NoOpCliktCommand() {
    override fun run() {
        context {
            allowInterspersedArgs = true
        }
        currentContext.findOrSetObject { env }
//        runBlocking {
//            env.network.launchNetwork()
//            env.network.awaitStability()
//        }
    }

    override fun aliases(): Map<String, List<String>> =
        registeredSubcommands().flatMap {
            it.aliases().toList()
        }.toMap()
}
