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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.CustomNetwork.CustomNetworkSpecs
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.Switch
import java.io.File

@OptIn(ExperimentalSerializationApi::class, ExperimentalKotest::class)
class CustomNetworkTest : FunSpec({
    val jsonReader = Json { ignoreUnknownKeys = true }
    val specsTest1File = File("src/test/resources/custom-network/specs-test2.json")
    val specsTest2File = File("src/test/resources/custom-network/specs-test2.json")
    val dupNodesFile = File("src/test/resources/custom-network/specs-duplicate-node-id.json")
    val undeployableLinksFile = File("src/test/resources/custom-network/specs-undeployable-link.json")
    val badLinkArraySizesFile = File("src/test/resources/custom-network/specs-bad-link-array-sizes.json")

    context("build network from json file") {

        specsTest1File.let { testFile ->
            context("convert json file to network specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()) }
            }
        }

        specsTest2File.let { testFile ->
            context("build network from specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).build() }
            }
        }

        context("build network from incorrect specs") {
            dupNodesFile.let { testFile ->
                context("duplicate node ids (eliminating duplicates)").config(enabled = testFile.exists()) {
                    shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).build() }
                }
            }

            undeployableLinksFile.let { testFile ->
                context("link between non-existing nodesById (ignoring)").config(enabled = testFile.exists()) {
                    shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).build() }
                }
            }

            badLinkArraySizesFile.let { testFile ->
                context("link arrays sizes not equal 2 (ignoring)").config(enabled = testFile.exists()) {
                    shouldNotThrowAny {
                        val customNet: CustomNetworkSpecs =
                            jsonReader.decodeFromStream<Specs<CustomNetwork>>(testFile.inputStream()) as CustomNetworkSpecs
                        customNet.links shouldContainExactlyInAnyOrder listOf(Pair(0L, 3L), Pair(0L, 4L))
                    }
                }
            }
        }
    }

    context("build correct links from link list") {
        data class TestData(val nodes: List<Node>, val linkList: List<Pair<NodeId, NodeId>>)

        fun Node.isConnectedTo(other: Node): Boolean = this.portToNode.contains(other.id)

        val testDataGenerator: Arb<TestData> =
            Arb.bind(
                Arb.int(2..20),
                Arb.int(1..10),
            ) { numOfNodes, numOfLinks ->
                val nodes: List<Node> =
                    buildList {
                        (0L..<numOfNodes).forEach { id -> add(Switch(id = id, numOfPorts = numOfNodes, portSpeed = DataRate.ZERO)) }
                    }
                val links: List<Pair<NodeId, NodeId>> =
                    buildList {
                        val arbNodeId = Arb.long(0L..<numOfNodes)
                        repeat(numOfLinks) { add(Pair(arbNodeId.next(), arbNodeId.next())) }
                    }
                TestData(nodes, links)
            }

        checkAll(iterations = 100, testDataGenerator) { testData ->
            context("Test with nodesById ${testData.nodes} and links ${testData.linkList}") {
                val nodes: List<Node> = testData.nodes
                val links: List<Pair<NodeId, NodeId>> = testData.linkList
                val network = CustomNetwork(nodes)
                network.connectFromLinkList(links)
                withData(links) { link ->
                    if (link.first != link.second) {
                        network.nodesById[link.first]
                            ?.isConnectedTo(network.nodesById[link.second]!!)
                            ?.shouldBeTrue()
                    } else {
                        network.nodesById[link.first]
                            ?.isConnectedTo(network.nodesById[link.second]!!)
                            ?.shouldBeFalse()
                    }
                }
            }
        }
    }
})
