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
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.node.NodeId
import org.opendc.simulator.network.components.stability.NetworkStabilityBarrier
import org.opendc.simulator.network.components.stability.NetworkStabilityChecker
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.SealedProtectedUse
import org.opendc.simulator.network.utils.errAndNull
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.warnAndNull

/**
 * Interface representing a network of [Node]s.
 */
@OptIn(SealedProtectedUse::class)
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
public sealed class Network protected constructor() : WithSpecs<Network> {
    internal val validator: NetworkStabilityBarrier = NetworkStabilityBarrier()

    private val networkScope =
        CoroutineScope(
            Dispatchers.Default +
                SupervisorJob() +
                (validator as NetworkStabilityChecker),
        )

    /**
     * Maps [NodeId]s to their corresponding [Node]s, which are part of the [Network]
     */
    internal open val nodesById: MutableMap<NodeId, Node> = mutableMapOf()

    /**
     * Maps [NodeId]s to their corresponding [EndPointNode]s, which are part of the [Network].
     * This map is a subset of [nodesById].
     */
    internal open val endPointNodes: MutableMap<NodeId, EndPointNode> = mutableMapOf()

    /**
     * Maps flow ids to their corresponding [NetFlow].
     */
    internal open val flowsById: MutableMap<FlowId, NetFlow> = mutableMapOf()

    protected open val flowsByName: MutableMap<String, NetFlow> = mutableMapOf()

    internal abstract val internet: Internet

    internal var runnerJob: Job? = null
        private set

    internal val isRunning: Boolean
        get() = runnerJob?.isActive ?: false

    /**
     * Launches a coroutine with a child coroutine for each node in the [networkScope].
     * The [networkScope] is responsible for running the whole network.
     *
     * @return The [Job] associated with the coroutine.
     */
    internal fun launchNetwork(): Job {
        runBlocking { runnerJob?.cancelAndJoin() }
        runnerJob = networkScope.launch(Dispatchers.Default) {
            validator.reset()
            this@Network.nodesById.values.forEach { n ->
                launch { n.run(validator.Invalidator()) }
            }
        }

        return runnerJob!!
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Start/Stop Flows
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starts a [NetFlow] if the flow can be established.
     * @param[flow] the flow to be established.
     * @return `null` if the flow could not be started, otherwise the flow itself.
     */
    internal suspend fun startFlow(flow: NetFlow): NetFlow? {
        // If name defined and already existed.
        if (flow.name != NetFlow.DEFAULT_NAME && flow.name in this.flowsByName) {
            return null
        }

        if (flow.getDemand() < DataRate.ZERO) {
            return log.errAndNull("Unable to start flow, data rate should be >= 0.")
        }

        val sender: EndPointNode =
            this.endPointNodes[flow.transmitterId]
                ?: return log.errAndNull("Unable to start flow $flow, sender does not exist or it is not able to start a flow")

        val receiver: EndPointNode =
            this.endPointNodes[flow.destinationId]
                ?: return log.errAndNull("Unable to start flow $flow, receiver does not exist or it is not able to start a flow")

        this.flowsById[flow.id] = flow
        if (flow.name != NetFlow.DEFAULT_NAME) {
            this.flowsByName[flow.name] = flow
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
        this.flowsById[flowId]?.let { eToEFlow ->
            this.endPointNodes[eToEFlow.transmitterId]
                ?.stopFlow(eToEFlow.id)
                ?.let {
                    this.endPointNodes[eToEFlow.destinationId]
                        ?.rmReceivingEtoEFlow(eToEFlow.id)
                    this.flowsById.remove(flowId)
                    eToEFlow
                }
        } ?: log.warnAndNull("unable to stop flow with id $flowId")

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Virtual Simulation Time
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    internal suspend fun advanceBy(time: Time) {
        validator.awaitStability()
        this.flowsById.values.forEach { it.advanceBy(time) }
    }

    /**
     * @see NetworkStabilityBarrier.awaitStability
     */
    internal suspend fun awaitStability() {
        validator.awaitStability()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Info Formatting
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public fun fmtNodes(): String =
        "\n" +
            """
            | === NETWORK INFO ===
            | num of core switches: ${getNodesById<CoreSwitch>().size}
            | num of host nodesById: ${getNodesById<HostNode>().size}
            | num of nodes: ${this.nodesById.size} (including INTERNET abstract node)
            """.trimIndent()

    public suspend fun fmtFlows(): String =
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
            this@Network.flowsById.values.forEach { flow ->
                appendLine(
                    "| " +
                        flow.id.toString().padEnd(5) +
                        flow.transmitterId.toString().padEnd(10) +
                        flow.destinationId.toString().padEnd(10) +
                        flow.getDemand().fmtValue("%.3f").padEnd(20) +
                        flow.getThroughput().fmtValue("%.3f").padEnd(20),
                )
            }
        }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Other
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    internal operator fun get(nId: NodeId): Node? = this.nodesById[nId]

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
