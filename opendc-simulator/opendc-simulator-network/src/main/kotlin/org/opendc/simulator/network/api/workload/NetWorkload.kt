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

import kotlinx.serialization.Serializer
import org.opendc.common.logger.logger
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.NetSimExp
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.NodeId
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

/**
 * Represent a network workload consisting of multiple [NetworkEvent]s.
 *
 * **This class is mutable**, its _events are consumed when executed.
 */
public class NetWorkload(
    networkEvents: Collection<NetworkEvent>,
) {

    internal val events: List<NetworkEvent> get() = _events.toList()
    private val _events: Queue<NetworkEvent> = LinkedList(networkEvents.sorted())

    /**
     * The instant of the first [NetworkEvent] of the workload.
     */
    public val startInstant: Instant =
        this._events.peek()?.deadline?.toInstant()
            ?: Instant.ofEpochMilli(0L)

    /**
     * The instant of the last [NetworkEvent] of the workload.
     */
    public val endInstant: Instant =
        this._events.lastOrNull()?.deadline?.toInstant()
            ?: Instant.ofEpochMilli(0L)

    /**
     * The number of [NetworkEvent]s that have not been executed yet.
     */
    public val numRemainingEvents: Int = _events.size
//
//    private val hostIds: Set<NodeId> =
//        buildSet {
//            _events.forEach { addAll(it.involvedIds()) }
//        }.filterNot { it == INTERNET_ID }.toSet()

    init {
        check(_events.isNotEmpty()) { "Network workload is empty." }
    }

//    /**
//     * If this method successfully completes, the controller is then able to execute ***this*** workload,
//     * even if the workload node ids do not correspond to physical node ids.
//     *
//     * @param[controller]   the [NetworkController] on which to perform the mapping.
//     */
//    internal fun performVirtualMappingOn(controller: NetworkController) {
//        // vId = virtual id
//        // pId = physical id
//
//        // map host node ids of the workload to physical host nodesById of the network
//        hostIds.forEach { vId ->
//            checkNotNull(
//                controller.claimNextHostNode()?.nodeId?.let {
//                        pId ->
//                    controller.virtualMap(vId, pId)
//                },
//            ) { "unable to map workload to network, not enough host nodes claimable in the network (${hostIds.size} needed)" }
//        }
//    }

//    /**
//     * Executes the next [NetworkEvent].
//     */
//    internal suspend fun NetworkController.execNext() {
//        _events.poll()?.let { with(it) { execIfNotPassed() } }
//            ?: LOG.error("unable to execute network event, no more _events remaining in the workload")
//    }

    /**
     * @return `true` if there is at least one [NetworkEvent] that has not been executed, `false` otherwise.
     */
    internal fun hasNext(): Boolean = _events.isNotEmpty()

//    internal suspend fun NetworkController.execUntil(
//        until: Timestamp,
//        workChl: CoroutineWorkChannel<NetworkEvent>,
//    ): Long {
//        var consumed: Long = 0
//        while ((_events.peek()?.deadline ?: Timestamp.ofEpochMs(Long.MAX_VALUE)) <= until) {
//            workChl.send(_events.poll())
//            consumed++
//        }
//        coroutineScope {
// //            while ((_events.peek()?.deadline ?: Timestamp.ofEpochMs(Long.MAX_VALUE)) <= until) {
// //                _events.poll()?.let { with(it) { launch { execIfNotPassed() } } }
// //                consumed++
// //            }
//            pollUntil(until).map { launch { it.execIfNotPassed() } }.also { consumed += it.size }
//        }
//
//        advanceBy(until.timeDelta(lastUpdate))
//
//        return consumed
//    }

    private fun pollUntil(until: Timestamp): Collection<NetworkEvent> =
        buildList {
            while ((_events.peek()?.deadline ?: Timestamp.ofEpochMs(Long.MAX_VALUE)) <= until) {
                add(_events.poll())
            }
        }

    internal fun peek(): NetworkEvent? = _events.peek()

    internal fun poll(): NetworkEvent? = _events.poll()

    public fun fmt(): String =
        """
        | === NETWORK WORKLOAD ===
        | start instant: $startInstant
        | end instant: $endInstant
        | duration: ${Duration.ofMillis(endInstant.toEpochMilli() - startInstant.toEpochMilli())}
        | remaining _events: ${_events.size}
        """.trimIndent()

    public fun copy(networkEvents: Collection<NetworkEvent> = _events): NetWorkload = NetWorkload(networkEvents)

    public companion object {
        internal val LOG by logger()
    }
}
