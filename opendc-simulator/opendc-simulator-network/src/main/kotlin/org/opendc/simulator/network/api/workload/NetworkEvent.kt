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

package org.opendc.simulator.network.api.workload

import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.logger

/**
 * Represents a single network event occurring at [deadline].
 * @see[FlowStart]
 * @see[FlowStop]
 * @see[FlowUpdateDemand]
 */
public abstract class NetworkEvent : Comparable<NetworkEvent> {
    private companion object {
        private val log by logger()
    }

    /**
     * The moment this event occurs.
     */
    internal abstract val deadline: Time

    /**
     * Often network events flow ids are not yet determined when they are created,
     * each event can retrieve its target flow from the event (of the same flow) that occurred before.
     */
    internal val targetFlow: NetFlow get() = targetFlowGetter()

    /**
     * Retrieves the target [NetFlow] from a [NetworkEvent] (of the same flow) that occurred earlier,
     * thus it must have its target flow determined.
     */
    protected open var targetFlowGetter: () -> NetFlow = {
        throw RuntimeException(
            "target flow for network event $this is not defined yet",
        )
    }

    /**
     * The [NodeId]s involved in this [NetworkEvent].
     */
    internal open fun involvedIds(): Set<NodeId> = setOf()

    /**
     * Executes *this* event on [this] controller.
     */
    protected abstract suspend fun NetworkController.exec()

    /**
     * Executes *this* event on [this] controller if the deadline is not passed.
     */
    internal suspend fun NetworkController.execIfNotPassed() {
        val msSinceLastUpdate: Time = deadline - Time.ofInstantFromEpoch(instantSrc.instant())
        if (msSinceLastUpdate < Time.ZERO) {
            return log.error(
                "unable to execute network event, " +
                    "deadline is passed (deadline=${deadline.toInstantFromEpoch()}, " +
                    "currentInstant=${instantSrc.instant()})",
            )
        }

        this.advanceBy(msSinceLastUpdate)

        exec()
    }

    override fun compareTo(other: NetworkEvent): Int = this.deadline.compareTo(other.deadline)

    /**
     * [NetworkEvent] that updates the demand of a [NetFlow] with [newDemand].
     */
    internal data class FlowUpdateDemand(
        override val deadline: Time,
        val newDemand: DataRate,
        override var targetFlowGetter: () -> NetFlow,
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            val flow = targetFlow
            flow.setDemand(newDemand)
        }
    }

    /**
     * [NetworkEvent] that starts a new flow from the [Node] with id [from]
     * to the [Node] with id [to] with initial demand [demand] and flow id [flowId].
     */
    internal data class FlowStart(
        override val deadline: Time,
        val from: NodeId,
        val to: NodeId,
        val demand: DataRate,
        val flowId: FlowId = runBlocking { NetFlow.nextId() },
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startFlow(
                transmitterId = from,
                destinationId = to,
                demand = demand,
            ) ?. let { targetFlowGetter = { it } }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)
    }

    /**
     * [NetworkEvent] that stops a [NetFlow].
     */
    internal data class FlowStop(
        override val deadline: Time,
        override var targetFlowGetter: () -> NetFlow,
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.stopFlow(
                flowId = targetFlow.id,
            )
        }
    }
}
