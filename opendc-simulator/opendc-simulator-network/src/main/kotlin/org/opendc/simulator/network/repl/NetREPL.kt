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
import kotlinx.coroutines.delay
import org.opendc.simulator.network.components.networks.custom.CustomNetwork
import org.opendc.simulator.network.repl.cmds.AdvTimeCmd
import org.opendc.simulator.network.repl.cmds.EnRepCmd
import org.opendc.simulator.network.repl.cmds.ExportCmd
import org.opendc.simulator.network.repl.cmds.ImportCmd
import org.opendc.simulator.network.repl.cmds.QuitCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowExportCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowInfoCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowMkCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowRmCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowSynthWlCmd
import org.opendc.simulator.network.repl.cmds.flow.FlowUpdtCmd
import org.opendc.simulator.network.repl.cmds.link.LinkCmd
import org.opendc.simulator.network.repl.cmds.link.LinkMkCmd
import org.opendc.simulator.network.repl.cmds.link.LinkRmCmd
import org.opendc.simulator.network.repl.cmds.network.NetCmd
import org.opendc.simulator.network.repl.cmds.network.NetInfoCmd
import org.opendc.simulator.network.repl.cmds.network.NetPrefixTrieCmd
import org.opendc.simulator.network.repl.cmds.network.NetSnapCmd
import org.opendc.simulator.network.repl.cmds.node.NodeCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkGlobalSwitchCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkTerminalCmd
import org.opendc.simulator.network.repl.cmds.node.NodeMkSwitchCmd
import org.opendc.simulator.network.repl.cmds.node.NodeRmCmd
import org.opendc.simulator.network.repl.cmds.node.NodeSnapCmd
import org.opendc.simulator.network.simscope.NetSimRootScope

public suspend fun main() {
    // The REPL is initialized with default configuration.
    val scope = NetSimRootScope()
    // Build an empty [CustomNetwork] in the initial scope.
    scope.launch { CustomNetwork() }
    // The mutable REPL environment that wraps the network scope (allowing the env to be replaced with import command).
    val env = NetREPLEnv(scope)

    // Build the Clikt command structure.
    val cmd: CliktCommand =
        MainCmd(env).subcommands(
            LinkCmd().subcommands(LinkMkCmd(), LinkRmCmd()),
            NodeCmd().subcommands(
                NodeRmCmd(),
                NodeMkCmd().subcommands(NodeMkTerminalCmd(), NodeMkSwitchCmd(), NodeMkGlobalSwitchCmd()),
                NodeSnapCmd(),
            ),
            AdvTimeCmd(),
            FlowCmd().subcommands(
                FlowMkCmd(),
                FlowInfoCmd(),
                FlowRmCmd(),
                FlowUpdtCmd(),
                FlowSynthWlCmd(),
                FlowExportCmd(),
            ),
            EnRepCmd(),
            ExportCmd(),
            NetCmd().subcommands(
                NetSnapCmd(),
                NetInfoCmd(),
                NetPrefixTrieCmd(),
            ),
            ImportCmd(),
            QuitCmd(),
        )

    // Let logger log stuff before starting the REPL loop.
    delay(1000L)

    // REPL loop.
    while (true) {
        print("> ") // Prompt
        val input: String = readln()
        val inputArr: List<String> = input.trim().split("\\s+".toRegex())

        try {
            cmd.parse(inputArr)
        } catch (e: PrintHelpMessage) {
            println(e.command.getFormattedHelp())
        } catch (e: CliktError) {
            e.message?.let { println(it) }
//        } catch (e: Exception) {
//            println("Unexpected error: ${e.message}")
        }
    }
}

private class MainCmd(private val env: NetREPLEnv) : NoOpCliktCommand() {
    override fun run() {
        context {
            allowInterspersedArgs = true
        }
        // Set the currently active [NetSimRootScope] clikt context.
        currentContext.findOrSetObject { env }
    }

    /**
     * Recursively registers aliases for the command structure.
     */
    override fun aliases(): Map<String, List<String>> =
        registeredSubcommands().flatMap {
            it.aliases().toList()
        }.toMap()
}
