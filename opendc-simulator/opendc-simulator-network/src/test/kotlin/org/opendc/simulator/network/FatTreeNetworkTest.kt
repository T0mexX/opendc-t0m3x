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

package org.opendc.simulator.network

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.FatTreeNetwork
import org.opendc.simulator.network.components.FatTreeNetwork.FatTreeTopologySpecs
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Switch.SwitchSpecs
import org.opendc.simulator.network.components.specs.Specs
import java.io.File
import kotlin.math.pow

@OptIn(ExperimentalSerializationApi::class, ExperimentalKotest::class)
class FatTreeNetworkTest : FunSpec({
    val jsonReader = Json { ignoreUnknownKeys = true }

    context("convert json file to network specs") {
        File("src/test/resources/fat-tree-network/specs-k4.json").let { testFile ->
            context("convert json with generic switch specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()) }
            }
        }

        File("src/test/resources/fat-tree-network/specs-k4-override-layer.json").let { testFile ->
            context("convert json with per layer switch specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).build() }
            }
        }
    }

    context("build fat-tree from specs") {
        withData(
            SwitchSpecs(numOfPorts = 4, portSpeed = DataRate.zero),
            SwitchSpecs(numOfPorts = 6, portSpeed = DataRate.zero),
            SwitchSpecs(numOfPorts = 8, portSpeed = DataRate.zero),
//            SwitchSpecs(nPorts = 10, portSpeed = .0)
        ) { switchSpecs ->
            val k: Int = switchSpecs.numOfPorts
            val fatTree: FatTreeNetwork =
                FatTreeTopologySpecs(
                    switchSpecs = switchSpecs,
                    hostNodeSpecs = HostNode.HostNodeSpecs(numOfPorts = 1, portSpeed = DataRate.ofGBps(1.0)),
                ).build()
            fatTree.pods.size shouldBeExactly k
            fatTree.leafs.size shouldBeExactly (k / 2).toDouble().pow(2).toInt() * k
            fatTree.torSwitches.size shouldBeExactly k * k / 2
            fatTree.aggregationSwitches.size shouldBeExactly k * k / 2
            fatTree.coreSwitches.size shouldBeExactly k * k / 4
            fatTree.endPointNodes.size shouldBeExactly (k * k * k) / 4 + k * k / 4 + 1 // INTERNET_ID
        }
    }
})
