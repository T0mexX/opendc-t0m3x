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

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.repl.REPLTmSrc
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NETWORK_JSON
import java.io.File
import java.time.Instant
import kotlin.coroutines.EmptyCoroutineContext

private const val CMD_STR: String = "import"

internal class ImportCmd : REPLCmd(CMD_STR) {
    private val targetFile: File by argument(
        help = "Where the network will be exported",
    ).file()

    @OptIn(ExperimentalSerializationApi::class)
    override fun run(): Unit = execREPLCmdCatching(EmptyCoroutineContext) {
        val newScope = NETWORK_JSON.decodeFromStream<NetSimScope>(targetFile.inputStream())

        // Create a new simulation scope.
        scope.cancel()
        env.scope = newScope

        echo("Network simulation scope imported successfully.")
    }
}
