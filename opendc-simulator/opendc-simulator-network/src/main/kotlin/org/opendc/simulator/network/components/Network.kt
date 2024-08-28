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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.stability.NetworkStabilityChecker
import org.opendc.simulator.network.components.stability.NetworkStabilityValidator
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.errAndNull
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.warnAndNull

/**
 * Interface representing a network of [Node]s.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
public sealed class Network : WithSpecs<Network> {
    @Transient
    internal val validator: NetworkStabilityValidator = NetworkStabilityValidator()

    private val networkScope =
        CoroutineScope(
            Dispatchers.Default +
                SupervisorJob() +
                (validator as NetworkStabilityChecker),
        )

    /**
     * Maps [NodeId]s to their corresponding [Node]s, which are part of the [Network]
     */
    internal abstract val nodesById: Map<NodeId, Node>

    /**
     * Maps [NodeId]s to their corresponding [EndPointNode]s, which are part of the [Network].
     * This map is a subset of [nodesById].
     */
    internal abstract val endPointNodes: Map<NodeId, EndPointNode>

    /**
     * Maps flow ids to their corresponding [NetFlow].
     */
    @Transient
    internal val flowsById = mutableMapOf<FlowId, NetFlow>()

    @Transient
    internal val flowsByName = mutableMapOf<String, NetFlow>()

    internal abstract val internet: Internet

    internal var runnerJob: Job? = null
        private set

    internal val isRunning: Boolean
        get() = runnerJob?.isActive ?: false

    /**
     * Starts a [NetFlow] if the flow can be established.
     * @param[flow] the flow to be established.
     * @return `null` if the flow could not be started, otherwise the flow itself.
     */
    internal suspend fun startFlow(flow: NetFlow): NetFlow? {
        // If name defined and already exists.
        if (flow.name != NetFlow.DEFAULT_NAME && flow.name in flowsByName) {
            return null
        }

        if (flow.demand < DataRate.ZERO) {
            return log.errAndNull("Unable to start flow, data rate should be >= 0.")
        }

        val sender: EndPointNode =
            endPointNodes[flow.transmitterId]
                ?: return log.errAndNull("Unable to start flow $flow, sender does not exist or it is not able to start a flow")

        val receiver: EndPointNode =
            endPointNodes[flow.destinationId]
                ?: return log.errAndNull("Unable to start flow $flow, receiver does not exist or it is not able to start a flow")

        flowsById[flow.id] = flow
        if (flow.name != NetFlow.DEFAULT_NAME) {
            flowsByName[flow.name] = flow
        }

        receiver.addReceivingEtoEFlow(flow)

        sender.startFlow(flow)

        return flow
    }

    /**
     * Stops a [NetFlow] if the flow is running through the network.
     * @param[flowId]   id of the flow to be stopped.
     */
    internal suspend fun stopFlow(flowId: FlowId): NetFlow? =
        flowsById[flowId]?.let { eToEFlow ->
            endPointNodes[eToEFlow.transmitterId]
                ?.stopFlow(eToEFlow.id)
                ?.let {
                    endPointNodes[eToEFlow.destinationId]
                        ?.rmReceivingEtoEFlow(eToEFlow.id)
                    flowsById.remove(flowId)
                    eToEFlow
                }
        } ?: log.warnAndNull("unable to stop flow with id $flowId")

    internal fun resetFlows() =
        runBlocking {
            flowsById.keys.toSet().forEach { stopFlow(it) }
        }

    /**
     * Returns a string with all [nodesById] string representations, each in one line.
     */
    internal fun allNodesToString(): String {
        val sb = StringBuilder()
        nodesById.forEach { sb.append("\n$it") }
        sb.append("\n")

        return sb.toString()
    }

    internal suspend fun advanceBy(time: Time) {
        flowsById.values.forEach { it.advanceBy(time) }
    }

    internal suspend fun awaitStability() {
        validator.awaitStability()
    }

    internal fun launch(): Job {
        runBlocking { runnerJob?.cancelAndJoin() }
        validator.reset()
        runnerJob =
            networkScope.launch {
                nodesById.forEach { (_, n) ->
                    launch {
                        n.run(validator.Invalidator())
                    }
                }
            }

        return runnerJob!!
    }

    public fun fmtNodes(): String =
        "\n" +
            """
            | === NETWORK INFO ===
            | num of core switches: ${getNodesById<CoreSwitch>().size}
            | num of host nodesById: ${getNodesById<HostNode>().size}
            | num of nodes: ${nodesById.size} (including INTERNET abstract node)
            """.trimIndent()

    public fun fmtFlows(): String =
        buildString {
            appendLine("| ==== Flows ====")
            appendLine(
                "| " +
                    "id".padEnd(5) +
                    "sender".padEnd(10) +
                    "dest".padEnd(10) +
                    "demand".padEnd(20) +
                    "throughput".padEnd(20),
            )
            flowsById.values.forEach { flow ->
                appendLine(
                    "| " +
                        flow.id.toString().padEnd(5) +
                        flow.transmitterId.toString().padEnd(10) +
                        flow.destinationId.toString().padEnd(10) +
                        flow.demand.fmtValue("%.3f").padEnd(20) +
                        flow.throughput.fmtValue("%.3f").padEnd(20),
                )
            }
        }

    internal operator fun get(nId: NodeId): Node? = nodesById[nId]

    public companion object {
        private val log by logger()

        internal inline fun <reified T : Node> Network.getNodesById(): Map<NodeId, T> {
            return this.nodesById.values.filterIsInstance<T>().associateBy { it.id }
        }

        /**
         * [NodeId] reserved for internet representation (for inter-datacenter communication).
         */
        public const val INTERNET_ID: NodeId = NodeId.MIN_VALUE
    }
}
