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

package org.opendc.simulator.network.components

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.node.NodeId
import org.opendc.simulator.network.components.HostNode.HostNodeSpecs
import org.opendc.simulator.network.components.Switch.SwitchSpecs
import org.opendc.simulator.network.utils.NonSerializable
import kotlin.math.min
import kotlin.math.pow

/**
 * Fat-tree network topology built based on the number of ports of the [SwitchSpecs] passed as parameter.
 * The number of ports is usually referred to as ***k*** and should be a multiple of 2 and larger than 2.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class FatTreeNetwork(
    private val coreSpecs: SwitchSpecs,
    private val aggrSpecs: SwitchSpecs,
    private val torSpecs: SwitchSpecs,
    private val hostNodeSpecs: HostNodeSpecs,
) : Network() {
    override val nodesById: MutableMap<NodeId, Node>
    override val endPointNodes: MutableMap<NodeId, EndPointNode>

    /**
     * Parameter that determines the topology which is defined as
     * equal to the minimum number of ports of all switches rounded down to even number.
     * Ideally, all switches should have the same number of ports.
     * This value has to be even and larger than 2.
     */
    private val k: Int = minOf(coreSpecs.numOfPorts, aggrSpecs.numOfPorts, torSpecs.numOfPorts) / 2 * 2

    /**
     * The [FatTreePod]s that belong to ***this*** [FatTreeNetwork].
     */
    val pods: List<FatTreePod>

    /**
     * The [CoreSwitch]es that belong to ***this*** [FatTreeNetwork].
     */
    val coreSwitches: List<Switch>

    /**
     * The [HostNode]s that belong to ***this*** [FatTreeNetwork].
     */
    val leafs: List<HostNode>

    /**
     * The aggregation [Switch]es that belong to ***this*** [FatTreeNetwork].
     */
    val aggregationSwitches: List<Switch>

    /**
     * The Top of Rack [Switch]es that belong to ***this*** [FatTreeNetwork].
     */
    val torSwitches: List<Switch>

    override val internet: Internet

    init {
        require(k.isEven() && k > 2) { "Fat tree can only be built with even-port-number (>2) switches" }

        // to connect to abstract node "internet"
        val coreSpecs = coreSpecs.copy(numOfPorts = coreSpecs.numOfPorts + 1)

        log.info("building fat-tree with k=$k")
        pods = buildList { repeat(k) { add(FatTreePod(aggrSpecs, torSpecs, hostNodeSpecs)) } }

        val coreSwitchesChunked =
            buildList {
                repeat(k * k / 4) { add(coreSpecs.buildCoreSwitchFromSpecs()) }
            }.chunked(k / 2)

        pods.forEach { pod ->
            pod.aggrSwitches.forEachIndexed { switchIdx, switch ->
                coreSwitchesChunked[switchIdx].forEach { runBlocking { it.connect(switch) } }
            }
        }

        coreSwitches = coreSwitchesChunked.flatten()

        aggregationSwitches = pods.flatMap { it.aggrSwitches }

        torSwitches = pods.flatMap { it.torSwitches }

        leafs = pods.flatMap { it.hostNodes }

        internet = Internet().connectedTo(coreSwitches)

        nodesById =
            buildMap {
                putAll((leafs + torSwitches + aggregationSwitches + coreSwitches).associateBy { it.id })
                check(
                    internet.id !in this,
                ) { "unable to create network: one node has id $INTERNET_ID, which is reserved for internet abstraction" }
                put(internet.id, internet)
            }.toMutableMap()

        endPointNodes = (coreSwitches + leafs + internet).associateBy { it.id }.toMutableMap()
    }

    override fun toSpecs(): Specs<FatTreeNetwork> =
        FatTreeTopologySpecs(
            coreSwitchSpecs = coreSpecs,
            aggrSwitchSpecs = aggrSpecs,
            torSwitchSpecs = torSpecs,
            hostNodeSpecs = hostNodeSpecs,
        )

    /**
     * A pod that belongs to the enclosing [FatTreeNetwork] instance.
     * @param[aggrSpecs]    specifications of the switches in the *aggregation layer*.
     * @param[torSpecs]     specifications of the switches in the *access layer* (also called Top of Rack switches).
     */
    inner class FatTreePod(
        aggrSpecs: SwitchSpecs,
        torSpecs: SwitchSpecs,
        hostNodeSpecs: HostNodeSpecs,
    ) {
        /**
         * [HostNode]s that belong to ***this*** pod.
         */
        val hostNodes: List<HostNode>

        /**
         * ToR [Switch]es that belong to ***this*** pod.
         */
        val torSwitches: List<Switch>

        /**
         * Aggregation [Switch]es that belong to ***this*** pod.
         */
        val aggrSwitches: List<Switch>

        init {
            val k: Int = min(aggrSpecs.numOfPorts, torSpecs.numOfPorts)
            hostNodes =
                buildList {
                    repeat((k / 2).toDouble().pow(2.0).toInt()) { add(hostNodeSpecs.build()) }
                }

            torSwitches =
                buildList {
                    repeat(k / 2) { add(torSpecs.build()) }
                }

            hostNodes.forEachIndexed { index, server ->
                runBlocking { server.connect(torSwitches[index / (k / 2)]) }
            }

            aggrSwitches =
                torSwitches
                    .map { _ ->
                        val newSwitch = aggrSpecs.build()
                        torSwitches.forEach { runBlocking { newSwitch.connect(it) } }
                        newSwitch
                    }.toList()
        }
    }

    companion object {
        private val log by logger()
    }

    @Serializable
    @SerialName("fat-tree-specs")
    internal data class FatTreeTopologySpecs(
        val name: String = "Default",
        val switchSpecs: SwitchSpecs? = null,
        val coreSwitchSpecs: SwitchSpecs? = null,
        val aggrSwitchSpecs: SwitchSpecs? = null,
        val torSwitchSpecs: SwitchSpecs? = null,
        val hostNodeSpecs: HostNodeSpecs,
    ) : Specs<FatTreeNetwork> {
        /**
         * Returns a [FatTreeNetwork] if the specs are valid, throws error otherwise.
         */
        override fun build(): FatTreeNetwork {
            val error by lazy {
                IllegalArgumentException(
                    "Unable to build Fat-Tree from specs. " +
                        "Either define all layers specs or provide a general switch specs",
                )
            }
            return FatTreeNetwork(
                coreSpecs = coreSwitchSpecs ?: run { switchSpecs ?: throw error },
                aggrSpecs = aggrSwitchSpecs ?: run { switchSpecs ?: throw error },
                torSpecs = torSwitchSpecs ?: run { switchSpecs ?: throw error },
                hostNodeSpecs = hostNodeSpecs,
            )
        }
    }
}

private fun Int.isEven(): Boolean = this % 2 == 0
