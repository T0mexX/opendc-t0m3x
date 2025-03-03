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

import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.utils.logger
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

/**
 * Represent a network workload consisting of multiple [NetworkEvent]s.
 *
 * **This class is mutable**, its events are consumed when executed.
 */
public class SimNetWorkload(
    networkEvents: Collection<NetworkEvent>,
) {
    private val events: Queue<NetworkEvent> = LinkedList(networkEvents.sorted())

    /**
     * The instant of the first [NetworkEvent] of the workload.
     */
    public val startInstant: Instant =
        this.events.peek()?.deadline?.toInstantFromEpoch()
            ?: Instant.ofEpochMilli(0L)

    /**
     * The instant of the last [NetworkEvent] of the workload.
     */
    public val endInstant: Instant =
        this.events.lastOrNull()?.deadline?.toInstantFromEpoch()
            ?: Instant.ofEpochMilli(0L)

    /**
     * The number of [NetworkEvent]s that have not been executed yet.
     */
    public val numRemainingEvents: Int = events.size

    private val hostIds: Set<NodeId> =
        buildSet {
            events.forEach { addAll(it.involvedIds()) }
        }.filterNot { it == Network.INTERNET_ID }.toSet()

    init {
        check(events.isNotEmpty()) { "Network workload is empty." }
    }

    /**
     * If this method successfully completes, the controller is then able to execute ***this*** workload,
     * even if the workload node ids do not correspond to physical node ids.
     *
     * @param[controller]   the [NetworkController] on which to perform the mapping.
     */
    internal fun performVirtualMappingOn(controller: NetworkController) {
        // vId = virtual id
        // pId = physical id

        // map host node ids of the workload to physical host nodesById of the network
        hostIds.forEach { vId ->
            checkNotNull(
                controller.claimNextHostNode()?.nodeId?.let {
                        pId ->
                    controller.virtualMap(vId, pId)
                },
            ) { "unable to map workload to network, not enough host nodes claimable in the network (${hostIds.size} needed)" }
        }
    }

    /**
     * Executes the next [NetworkEvent].
     */
    internal suspend fun NetworkController.execNext() {
        events.poll()?.let { with(it) { execIfNotPassed() } }
            ?: LOG.error("unable to execute network event, no more events remaining in the workload")
    }

    /**
     * @return `true` if there is at least one [NetworkEvent] that has not been executed, `false` otherwise.
     */
    internal fun hasNext(): Boolean = events.isNotEmpty()

    internal suspend fun NetworkController.execUntil(until: Time): Long {
        var consumed: Long = 0

        while ((events.peek()?.deadline ?: Time.ofMillis(Long.MAX_VALUE)) <= until) {
            events.poll()?.let { with(it) { execIfNotPassed() } }
            consumed++
        }

        advanceBy(until - lastUpdate)

        return consumed
    }

    internal fun peek(): NetworkEvent = events.peek()

    public fun fmt(): String =
        """
        | == NETWORK WORKLOAD ===
        | start instant: $startInstant
        | end instant: $endInstant
        | duration: ${Duration.ofMillis(endInstant.toEpochMilli() - startInstant.toEpochMilli())}
        | num of network events: ${events.size}
        """.trimIndent()

    public fun copy(networkEvents: Collection<NetworkEvent> = events): SimNetWorkload = SimNetWorkload(networkEvents)

    public companion object {
        internal val LOG by logger()
    }
}
