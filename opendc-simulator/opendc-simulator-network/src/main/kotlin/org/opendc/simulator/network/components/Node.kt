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

import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.stability.NetworkStabilityValidator
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.utils.ifNull0

/**
 * Interface representing a node in a [Network].
 */
internal interface Node : FlowView, WithSpecs<Node> {
    // TODO: allow connection between nodesById without immediate vector routing forwarding,
    //  to optimize network building

    /**
     * ID of the node. Uniquely identifies the node in the [Network].
     */
    val id: NodeId

    /**
     * Port speed in Kbps full duplex.
     */
    val portSpeed: DataRate

    /**
     * Ports of ***this*** [Node], full duplex.
     */
    val ports: List<Port>

    /**
     * Number of ports of ***this*** [Node].
     */
    val numOfPorts: Int get() {
        return ports.size
    }

    /**
     * Policy that determines to which [Port]s the flows are forwarded to.
     */
    val portSelectionPolicy: PortSelectionPolicy

    /**
     * Policy that determines how the flows data are handled in case of maximum bw reached.
     */
    val fairnessPolicy: FairnessPolicy

    /**
     * Handles incoming and outgoing flows.
     */
    val flowHandler: FlowHandler

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routingTable: RoutingTable

    /**
     * Maps each connected [Node]'s id to the [Port] is connected to.
     */
    val portToNode: MutableMap<NodeId, Port>

    /**
     * Property returning the number of [Node]s connected to ***this***.
     */
    private val numOfConnectedNodes: Int
        get() {
            return ports.count { it.isConnected }
        }

    /**
     * Aggregates the flow updates from all adjacent nodes.
     */
    val updtChl: UpdateChl

    /**
     * **Does not return**. Should be launched as an independent coroutine.
     * Processes incoming updates.
     * @param[invalidator] used to invalidate the network stability while updates are pending or being processed.
     */
    suspend fun run(invalidator: NetworkStabilityValidator.Invalidator? = null) {
        invalidator?.let { updtChl.withInvalidator(invalidator) }
        updtChl.clear()

        while (true) {
            yield()
            consumeUpdt()
        }
    }

    /**
     * Consumes a round of updates.
     */
    suspend fun consumeUpdt() {
        var updt: RateUpdt = updtChl.receive()

        while (true) {
            yield()
            updtChl.tryReceiveSus().getOrNull()
                ?.also { updt = updt.merge(it) }
                ?: break
        }

        with(flowHandler) { updtFlows(updt) }

        notifyAdjNodes()
    }

    /**
     * Sends the buffered updates to adjacent nodes.
     */
    private suspend fun notifyAdjNodes() {
        portToNode.values.forEach { it.notifyReceiver() }
    }

    /**
     * Updates forwarding of all flows transiting through ***this*** node.
     */
    suspend fun updateAllFlows() {
        updtChl.send(RateUpdt(allTransitingFlowsIds().associateWith { DataRate.ZERO })) // TODO: change
    }

    override fun totIncomingDataRateOf(fId: FlowId): DataRate = flowHandler.outgoingFlows[fId]?.demand.ifNull0()

    override fun totOutgoingDataRateOf(fId: FlowId): DataRate = flowHandler.outgoingFlows[fId]?.totRateOut.ifNull0()

    override fun allTransitingFlowsIds(): Collection<FlowId> =
        with(flowHandler) {
            outgoingFlows.keys + consumingFlows.keys
        }

    /**
     * @return formatted string representing node information. Preferably to be logged in a new line.
     */
    fun fmt(): String =
        """
        | Type = ${this::class.simpleName}
        | numPorts = $numOfPorts
        | portSpeed = $portSpeed
        | numConnectedNodes = $numOfConnectedNodes
        """.trimIndent()
}
