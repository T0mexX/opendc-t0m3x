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

package org.opendc.simulator.network.input

import org.opendc.simulator.network.api.workload.NetworkEvent
import org.opendc.simulator.network.api.workload.NetworkEvent.FlowStart
import org.opendc.simulator.network.api.workload.NetworkEvent.FlowStop
import org.opendc.simulator.network.api.workload.SimNetWorkload
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.flow.publics.FlowId

internal class ImprintsToWlConverter private constructor(
    imprints: Collection<NetEventImprint>,
) {
    private val imprints: Collection<NetEventImprint> = imprints.sortedBy { it.deadline }

    // Used if flow ids are defined.
    private val encounteredFlowStartsByIds = mutableMapOf<FlowId, FlowStart>()
    private val encounteredFlowEndByIds = mutableMapOf<FlowId, FlowStop>()

    // Used if flow ids are not defined.
    private val encounteredFStartByNodesInvolved = mutableMapOf<Pair<NodeId, NodeId>, FlowStart>()
    private val encounteredFEndsByNodesInvolved = mutableMapOf<Pair<NodeId, NodeId>, FlowStop>()
    private val converted: MutableList<NetworkEvent> = ArrayList(imprints.size)

    /**
     * Converts [imprints] to a [SimNetWorkload].
     */
    private fun convert(): SimNetWorkload {
        imprints.forEach { it.convert() }
        val events = converted.sorted()

//        val hostIds =
//            buildSet { // TODO: remove this feature from workload and just
//                events.forEach { event ->
//                    addAll(event.involvedIds().filterNot { it == INTERNET_ID })
//                }
//            }

        return SimNetWorkload(networkEvents = events)
    }

    /**
     * Converts a single [NetEventImprint] to a [NetworkEvent], having the knowledge of the previous events.
     */
    private fun NetEventImprint.convert() {
        if (flowId == null) {
            flowIdNull()
        } else {
            flowIdNotNull()
        }
    }

    /**
     * Converts a single [this] to a [NetworkEvent] if [this.flowId] is `null`.
     */
    private fun NetEventImprint.flowIdNull() {
        // The nodes involved in the flow
        val nodesInvolved = Pair(transmitterId, destId)

        encounteredFStartByNodesInvolved[nodesInvolved]?.let { flowStart ->
            // Flow with these nodes involved started but end not scheduled.
            flowStart.addFlowRateChange()

            duration?.let {
                SimNetWorkload.LOG.warn(
                    "duration of flow specified on flow update instead of start, flow end schedule at update.deadline + duration",
                )
                flowStart.addFlowStop().also { encounteredFEndsByNodesInvolved[nodesInvolved] = it }
                encounteredFStartByNodesInvolved.remove(nodesInvolved)
            }

            return
        }

        encounteredFEndsByNodesInvolved[nodesInvolved]?.let { flowStop ->
            // Flow with these nodes involved started and end is scheduled.

            if (deadline < flowStop.deadline) {
                // Update before flow stops.

                check(duration == null) {
                    "unable to specify duration for this flow, its end is already scheduled"
                }

                flowStop.addFlowRateChange()
            } else {
                // Starts a new flow with same involved nodes after the previous one has ended.

                duration?.let {
                    addFlowStart()
                    flowStop.addFlowStop().also { encounteredFEndsByNodesInvolved[nodesInvolved] = it }
                } ?: let {
                    encounteredFStartByNodesInvolved[nodesInvolved] = addFlowStart()
                }
            }

            return
        }

        val flowStart = addFlowStart()

        duration?.let {
            flowStart.addFlowStop().also { encounteredFEndsByNodesInvolved[nodesInvolved] = it }
        } ?: let { encounteredFStartByNodesInvolved[nodesInvolved] = flowStart }
    }

    /**
     * Converts [this] to a [NetworkEvent] if [this.flowId] is not `null`.
     */
    private fun NetEventImprint.flowIdNotNull() {
        val flowId = flowId!!

        encounteredFlowStartsByIds[flowId]?.let { flowStart ->
            // Checks network events consistency (same flow id => same destination and transmitter).
            check(transmitterId == flowStart.from && destId == flowStart.to) {
                "Unable to read workload: 2 entries in workload have same flow id ($flowId) but different transmitter &/or destination ids"
            }

            check(duration == null) {
                "Unable to read workload: duration value should only be defined on the start of a flow, not on an update"
            }

            check(encounteredFlowEndByIds[flowId]?.deadline?.let { it >= deadline } ?: true) {
                "Unable to read workload: found data-rate update for flow ($flowId) after the flow stop event schedule"
            }

            flowStart.addFlowRateChange()
        } ?: let {
            check(destId != INTERNET_ID || transmitterId != INTERNET_ID)

            val flowStart = addFlowStart().also { encounteredFlowStartsByIds[flowId] = it }

            duration?.let {
                flowStart.addFlowStop().also { encounteredFlowEndByIds[flowId] = it }
            }
        }
    }

    context (NetEventImprint)
    private fun addFlowStart(): FlowStart =
        let {
            flowId?.let { fId ->
                FlowStart(
                    deadline = deadline,
                    from = transmitterId,
                    to = destId,
                    demand = netTx,
                    flowId = fId,
                )
            } ?: FlowStart(
                deadline = deadline,
                from = transmitterId,
                to = destId,
                demand = netTx,
            )
        }.also { converted += it }

    context (NetEventImprint)
    private fun NetworkEvent.addFlowStop(): FlowStop =
        FlowStop(
            deadline = this@NetEventImprint.deadline + duration!!,
            targetFlowGetter = { this.targetFlow },
        ).also { converted += it }

    context (NetEventImprint)
    private fun NetworkEvent.addFlowRateChange() =
        NetworkEvent.FlowUpdateDemand(
            deadline = this@NetEventImprint.deadline,
            newDemand = netTx,
            targetFlowGetter = { this.targetFlow },
        ).also { converted += it }

    companion object {
        fun Collection<NetEventImprint>.toWl(): SimNetWorkload = ImprintsToWlConverter(this).convert()
    }
}
