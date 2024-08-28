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

package org.opendc.simulator.network.flow

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.tracker.NodeFlowTracker
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.utils.logger

/**
 * Handles all incoming and outgoing flows of the node this handler belongs to,
 * keeping track of their demand and output rates.
 * Allows to generate and stop flows.
 *
 * Check properties and methods for more details.
 *
 * @param[ports]    ports of the node this handler belongs to.
 * Only used to provide the [availableBW].
 */
internal class FlowHandler(internal val ports: Collection<Port>) {
    private companion object {
        val log by logger()
    }

    /**
     * The current total available bandwidth on the switch,
     * as the sum of the available bw of the connected active ports.
     */
    val availableBW: DataRate
        get() = DataRate.ofKbps(ports.sumOf { it.sendLink?.availableBW?.toKbps() ?: .0 })

    /**
     * [NetFlow]s whose sender is the node to which this flow handler belongs.
     * Needs to be kept updated by owner [Node] through [generateFlow] and [stopGeneratedFlow].
     * If the node is not [EndPointNode] this property shall be ignored.
     */
    val generatingFlows: Map<FlowId, NetFlow> get() = _generatingFlows
    private val _generatingFlows = mutableMapOf<FlowId, NetFlow>()

    /**
     * [NetFlow]s whose destination is the node to which this flow handler belongs.
     * Needs to be kept updated by owner [Node]. If the node is not an
     * [EndPointNode] this property shall be ignored.
     */
    val consumingFlows: Map<FlowId, NetFlow> get() = _consumingFlows
    private val _consumingFlows = mutableMapOf<FlowId, NetFlow>()

    /**
     * Keeps track of the flows that this node consumed,
     * in case some of those flows are still in the network after they have
     * been stopped, and they arrive at destination.
     */
    private val consumedFlowsIds = mutableSetOf<FlowId>()

    /**
     * Keeps track of all outgoing flows in the form of [OutFlow]s.
     * It is updated internally whenever [updtFlows] is invoked.
     *
     * Entries are added if a new flow is received.
     * Entries are removed if their [OutFlow.demand] is set to 0.
     */
    val outgoingFlows: Map<FlowId, OutFlow> get() = _outgoingFlows
    private val _outgoingFlows = mutableMapOf<FlowId, OutFlow>()

    /**
     * Keeps track of outFlows according to specific sorting rules.
     */
    val nodeFlowTracker = NodeFlowTracker(allOutgoingFlows = _outgoingFlows)

    /**
     * Adds [flow] among those that are consumed by this node,
     * logging a warning if a flow with same id has been added previously.
     */
    fun addConsumingFlow(flow: NetFlow) {
        _consumingFlows.compute(flow.id) { _, oldFlow ->
            oldFlow?.let {
                log.warn("adding flow $flow (or at least its id) among the consuming ones multiple times")
            }
            flow
        }
    }

    fun rmConsumingFlow(fId: FlowId) {
        consumedFlowsIds += fId
        _consumingFlows.remove(fId) ?: let {
            log.warn("unable to remove flow with id $fId among those that are being received, not there.")
        }
    }

    /**
     * Adds [newFlow] in the [generatingFlows] table. Additionally, it queues the [RateUpdt]
     * associated to the new flow automatically. The caller does **NOT** need to queue
     * an update itself. An observer on [newFlow] for changes of [NetFlow.demand],
     * is set up. This observer queues a demand update whenever a change occurs.
     */
    suspend fun Node.generateFlow(newFlow: NetFlow) {
        val updt =
            RateUpdt(
                _generatingFlows.putIfAbsent(newFlow.id, newFlow)
                    // If flow with same id already present
                    ?. let { currFlow ->
                        log.error("adding generated flow whose id is already present. Replacing...")
                        _generatingFlows[newFlow.id] = newFlow
                        mapOf(newFlow.id to (newFlow.demand - currFlow.demand))
                        // Else
                    } ?: mapOf(newFlow.id to newFlow.demand),
            )

        // Sets up the handler of any data rate changes, propagating updates to other nodesById
        // changes of this flow data rate can be performed through a NetworkController,
        // the NetworkInterface of this node, or through the instance of the NetFlow itself.
        newFlow.withDemandOnChangeHandler { _, old, new ->
            if (old == new) return@withDemandOnChangeHandler

            if (new < DataRate.ZERO) {
                log.warn(
                    "unable to change generated flow with id '${newFlow.id}' " +
                        "data-rate to $new, data-rate should be positive. Falling back to 0",
                )
            }

            updtChl.send(RateUpdt(newFlow.id, (new - old)))
        }

        // Update to be processed by the node runner coroutine.
        updtChl.send(updt)
    }

    suspend fun Node.stopGeneratedFlow(fId: FlowId) {
        val removedFlow =
            _generatingFlows.remove(fId)
                ?: let {
                    log.error(
                        "unable to stop generated flow with id $fId in node $this, " +
                            "flow is not present in the generated flows table",
                    )
                    return
                }

        // Update to be processed by the node runner coroutine
        updtChl.send(RateUpdt(fId, -removedFlow.demand))
    }

    /**
     * Consumes a [RateUpdt], adjusting each [OutFlow] demand.
     * If new flows are received, a new [OutFlow] entry is added.
     * If the receiver [Node] is the destination of a flow then its
     * end-to-end throughput is adjusted.
     *
     * Automatically makes use of the node [PortSelectionPolicy] for selecting
     * outgoing ports for new flows. The node [FairnessPolicy] is applied
     * afterwords to determine the outgoing data rate for each flow.
     */
    suspend fun Node.updtFlows(updt: RateUpdt) {
        updt.forEach { (fId, dr) ->
            val deltaRate = dr.roundToIfWithinEpsilon(DataRate.ZERO)
            if (deltaRate.isZero()) return@forEach

            // if this node is the destination
            _consumingFlows[fId]?.let {
                it.throughput += deltaRate
                return@forEach
            }

            if (fId in consumedFlowsIds) {
                // If this node was the destination but the flow has been stopped => ignore.
                return@forEach
            }

            // else
            _outgoingFlows.getOrPut(fId) {
                // if flow is new
                val newFlow = OutFlow(id = fId, nodeFlowTracker = nodeFlowTracker)
                val outputPorts = with(this.portSelectionPolicy) { selectPorts(fId) }
                newFlow.setOutPorts(outputPorts)
                newFlow
            }.let {
                it.demand += deltaRate

                check(it.demand >= DataRate.ZERO) { "flowId=$fId, nodeId=${this.id}, demand=${it.demand}" }

                // if demand is 0 the entry is removed
                if (it.demand.roundToIfWithinEpsilon(DataRate.ZERO).isZero()) {
                    _outgoingFlows.remove(it.id)
                    nodeFlowTracker.remove(it)
                }
            }
        }

        with(this.fairnessPolicy) { applyPolicy(updt) }
    }

    /**
     * Updates all the outgoing ports associated with possible routs for each flow.
     * Outgoing ports are selected following the [PortSelectionPolicy] associated with the receiver node.
     * Needs to be called after topology changes.
     */
    suspend fun Node.updtAllRouts() {
        outgoingFlows.values.toList().forEach {
            val selectedPorts = with(portSelectionPolicy) { selectPorts(it.id) }
            it.setOutPorts(selectedPorts)
        }
    }
}
