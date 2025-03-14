/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.experiments.network.runner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.api.NetSimExp
import org.opendc.simulator.network.api.integration.NetSimGlobal
import java.io.File

/**
 * Main entrypoint of the application.
 */
public fun main(args: Array<String>): Unit = NetExpCmd().main(args)

/**
 * Represents the command for the Scenario experiments.
 */
internal class NetExpCmd : CliktCommand(name = "scenario") {
    /**
     * The path to the environment directory.
     */
    private val expPath by option("--scenario-path", help = "path to scenario file")
        .file(canBeDir = false, canBeFile = true)
        .defaultLazy { File("resources/example-exp/net-exp.json") }

    @OptIn(ExperimentalSerializationApi::class)
    override fun run() {
        NetSimGlobal.WITH_COMPUTE = false
        val exp: NetSimExp = NetSimGlobal.Serialization.JSON.decodeFromStream(expPath.inputStream())

        runBlocking {
            val runner = exp.runner()
            runner.run()
        }
    }
}
