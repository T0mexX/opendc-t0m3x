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

import org.opendc.common.logger.logger
import org.opendc.common.units.DataRate
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.NetSimWlRunner
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * Represents a single network event occurring at [deadline].
 * @see[FlowStart]
 * @see[FlowStop]
 * @see[FlowUpdateDemand]
 */
public sealed class NetworkEvent : Comparable<NetworkEvent> {
    private companion object {
        private val log by logger()
    }

    /**
     * The moment this event occurs.
     */
    internal abstract val deadline: Timestamp

    /**
     * Often network events flow ids are not yet determined when they are created,
     * each event can retrieve its target flow from the event (of the same flow) that occurred before.
     */
    internal val targetFlow: INetFlow get() = targetFlowGetter()

    /**
     * Retrieves the target [NetFlow] from a [NetworkEvent] (of the same flow) that occurred earlier,
     * thus it must have its target flow determined.
     */
    internal open var targetFlowGetter: () -> INetFlow = {
        throw RuntimeException(
            "target flow for network event $this is not defined yet",
        )
    }

    /**
     * Executes *this* event on [this] controller.
     */
    context(NetSimWlRunner)
    protected abstract suspend fun exec()

//    /**
//     * Executes *this* event on this controller if the deadline is not passed.
//     */
//    context(NetworkController)
//    internal suspend fun execIfNotPassed() {
//        val msSinceLastUpdate: TimeDelta = deadline.timeDelta(Timestamp.ofInstant(instantSrc.instant()))
//        if (msSinceLastUpdate < TimeDelta.zero) {
//            return log.error(
//                "unable to execute network event, " +
//                    "deadline is passed (deadline=${deadline.toInstant()}, " +
//                    "currentInstant=${instantSrc.instant()})",
//            )
//        }
//
//        exec()
//    }

    context(NetSimWlRunner)
    internal suspend fun execIfNotPassed() {
        val msSinceLastUpdate: TimeDelta = deadline.timeDelta(netScope.tmSrc.tmstamp)
        if (msSinceLastUpdate < TimeDelta.zero) {
            return log.error(
                "unable to execute network event, " +
                    "deadline is passed (deadline=${deadline.toInstant()}, " +
                    "currentInstant=${netScope.tmSrc.instant()})",
            )
        }

        exec()
    }

    override fun compareTo(other: NetworkEvent): Int = this.deadline.compareTo(other.deadline)

    /**
     * [NetworkEvent] that updates the demand of a [NetFlow] with [newDemand].
     */
    internal data class FlowUpdateDemand(
        override val deadline: Timestamp,
        val newDemand: DataRate,
        override var targetFlowGetter: () -> INetFlow,
    ) : NetworkEvent() {
        context(NetSimWlRunner)
        override suspend fun exec() = with(netScope) {
            val flow = targetFlow
            flow.setDemand(newDemand)
        }
    }

    /**
     * [NetworkEvent] that starts a new flow from the [Node] with id [from]
     * to the [Node] with id [to] with initial demand [demand] and flow id [id].
     */
    internal data class FlowStart(
        override val deadline: Timestamp,
        val from: NodeId,
        val to: NodeId,
        val demand: DataRate,
        val id: FlowId? = null,
    ) : NetworkEvent() {
        context(NetSimWlRunner)
        override suspend fun exec() = with(netScope) {
            val newFlow: INetFlow = devConfig.netFlowConfig.version(
                senderId = from,
                destId = to,
                demand = demand,
                id = id,
            )
            targetFlowGetter = { newFlow }
            net.startFlow(newFlow)
        }
    }

    /**
     * [NetworkEvent] that stops a [NetFlow].
     */
    internal data class FlowStop(
        override val deadline: Timestamp,
        override var targetFlowGetter: () -> INetFlow,
    ) : NetworkEvent() {
        context(NetSimWlRunner)
        override suspend fun exec() = with(netScope) {
            net.stopFlow(targetFlowGetter())
        }
    }
}
