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

package org.opendc.experiments.base.experiment.specs

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.decodeFromStream
import org.opendc.common.Dispatcher
import org.opendc.experiments.base.experiment.Scenario
import org.opendc.simulator.network.api.integration.NetController
import org.opendc.simulator.network.api.integration.NetSimGlobal
import org.opendc.simulator.network.simscope.NetSimScopeSpec
import java.io.File
import kotlin.coroutines.coroutineContext

@Serializable
public class ScenarioNetworkCtxSpec(
    public val pathToFile: String,
) {
    @Transient private val file: File = File(pathToFile)

    init {
        require(file.exists()) { "The provided path to the network context: $pathToFile does not exist" }
    }

    /**
     * Initializes the network simulation scope with all necessary resources.
     *
     * @return The controller through which the compute simulation can control the network simulation.
     */
    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun getNetworkController(
        scenario: Scenario,
        dispatcher: Dispatcher,
        seed: Long,
    ): NetController {
        //
        // Deserialize network simulation scope specs and inject externally handled elements.
        val scopeSpec =
            NetSimGlobal.Serialization.JSON.decodeFromStream<NetSimScopeSpec>(file.inputStream())
                .withInjectedSeed(seed)
                .withInjectedInstantSrc(dispatcher.timeSource)
                .withInjectedExportConfig(scenario.exportModelSpec.networkExportConfig)
                .withInjectedOutputFolder(File("${scenario.outputFolder}/raw-output/${scenario.id}/seed=$seed"))

        // Return the controller through which the compute simulation can control the associated network simulation.
//        println(coroutineContext[ContinuationInterceptor])\
        return NetController(ctx = coroutineContext + Dispatchers.Default, netScopeSpec = scopeSpec)
    }
}
