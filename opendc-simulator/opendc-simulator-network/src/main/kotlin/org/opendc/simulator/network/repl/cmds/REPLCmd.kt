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

package org.opendc.simulator.network.repl.cmds

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.opendc.simulator.network.api.integration.netBlking
import org.opendc.simulator.network.repl.NetREPLEnv
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NETWORK_JSON

internal abstract class REPLCmd(val name: String) : CliktCommand(name = name) {
    protected val env by requireObject<NetREPLEnv>()
    protected val scope by lazy { env.scope }
    protected val net by lazy { scope.net }

    override fun aliases(): Map<String, List<String>> =
        registeredSubcommands().flatMap {
            it.aliases().toList()
        }.toMap()

    fun execREPLCmdCatching(
        block: suspend NetSimScope.() -> Unit,
    ): Unit =
        netBlking(env.scope) {
//            runCatching {
                block()
//            }.let {
//                if (it.isFailure) {
//                    echoCmdErr(it.exceptionOrNull()!!)
//                }
//            }
        }

    protected fun echoCmdErr(e: Throwable) {
        echo("unable to execute command ${this@REPLCmd.commandName}.\n" +
            "reason: ${e.message}\n" +
            "cause: ${e.cause}",
            err = true,
        )
    }

    companion object {
        inline fun <reified T> decodeOrNull(str: String): T? =
            try {
                NETWORK_JSON.decodeFromString<T>(str)
            } catch (_: Exception) {
                null
            }
    }
}
