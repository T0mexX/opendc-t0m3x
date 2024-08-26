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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.stability.NetworkStabilityChecker.Key.getNetStabilityChecker
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.SuspOnChangeHandler
import kotlin.coroutines.coroutineContext

/**
 * Represents an end-to-end flow, meaning the flow from one [EndPointNode] to another.
 * This end-to-end flow can be split into multiple sub-flows along the path,
 * but ultimately each sub-flow arrives at destination.
 * @param[name]                     name of the flow if any.
 * @param[transmitterId]            id of the [EndPointNode] this end-to-end flow is generated from.
 * @param[destinationId]            id of the [EndPointNode] this end-to-end flow is directed to.
 * @param[demand]                   data rate generated by the sender.
 */
public class NetFlow internal constructor(
    public val name: String = DEFAULT_NAME,
    public val transmitterId: NodeId,
    public val destinationId: NodeId,
    demand: DataRate = DataRate.ZERO,
) {
    public val id: FlowId = runBlocking { nextId() }

    /**
     * Functions [(NetFlow, Kbps, Kbps) -> Unit] invoked whenever the throughput of the flow changes.
     */
    private val throughputOnChangeHandlers = mutableListOf<OnChangeHandler<NetFlow, DataRate>>()

    /**
     * Functions [(NetFlow, DataRate, Kbps) -> Unit] invoked whenever the demand of the flow changes.
     */
    private val demandOnChangeHandlers = mutableListOf<SuspOnChangeHandler<NetFlow, DataRate>>()

    /**
     * Total data transmitted since the start of the flow (in Kb).
     */
    private var totDataTransmitted: DataSize = DataSize.ZERO

    /**
     * The current demand of the flow (in Kbps).
     */
    public var demand: DataRate = demand
        private set
    private val demandMutex = Mutex()

    init {
        // Sets up the static destination id retrieval for the flow.
        _flowsDestIds[id] = destinationId
    }

    /**
     * 'Sus' stands for suspending, see [setDemand] for java compatibility.
     *
     * Updates the data rate demand for ***this*** flow.
     * Call observers change handlers, added with [withDemandOnChangeHandler].
     */
    @JvmSynthetic
    public suspend fun setDemandSus(newDemand: DataRate): Unit =
        demandMutex.withLock {
            val oldDemand = demand
            if (newDemand approx oldDemand) return
            demand = newDemand

            // calls observers handlers
            demandOnChangeHandlers.forEach {
                it.handleChange(this, oldDemand, newDemand)
            }
        }

    /**
     * Non suspending overload for java interoperability.
     */
    @JvmName("setDemand") // Makes it visible from java (normally inline classes related methods aren't
    public fun setDemand(newDemand: DataRate) {
        runBlocking { setDemandSus(newDemand) }
    }

    /**
     * The end-to-end throughput of the flow.
     *
     * This property should be updated only by the receiver node
     * coroutine job, hence synchronized update should be guaranteed.
     */
    public var throughput: DataRate = DataRate.ZERO
        internal set(new) =
            runBlocking {
                throughputMutex.withLock {
                    if (new == field) return@runBlocking
                    val old = field
                    field = if (new approx demand) demand else new.roundToIfWithinEpsilon(DataRate.ZERO)

                    throughputOnChangeHandlers.forEach {
                        it.handleChange(obj = this@NetFlow, oldValue = old, newValue = field)
                    }
                }
            }
    private val throughputMutex = Mutex()

    /**
     * Adds [f] among the functions invoked whenever the throughput of the flow changes.
     */
    public fun withThroughputOnChangeHandler(f: (NetFlow, DataRate, DataRate) -> Unit): NetFlow {
        throughputOnChangeHandlers.add(f)

        return this
    }

    /**
     * Adds [f] among the functions invoked whenever the demand of the flow changes.
     */
    internal fun withDemandOnChangeHandler(f: SuspOnChangeHandler<NetFlow, DataRate>): NetFlow {
        demandOnChangeHandlers.add(f)

        return this
    }

    /**
     * Advances the time for the flow, updating the total data
     * transmitted according to [time] milliseconds timelapse.
     */
    internal suspend fun advanceBy(time: Time) {
        coroutineContext.getNetStabilityChecker().checkIsStableWhile {
            totDataTransmitted += throughput * time
        }
    }

    /**
     * Invoked by the garbage collector whenever a flow is destroyed.
     * It is deprecated since it does not offer any guarantees to be invoked,
     * but guarantees in these case are not needed, removing some entries
     * is only for slightly improved performances.
     */
    @Suppress("removal")
    protected fun finalize() {
        _flowsDestIds.remove(this.id)
    }

    internal companion object {
        internal const val DEFAULT_NAME: String = "unknown"

        @VisibleForTesting
        internal fun reset() {
            nextId = 0
            _flowsDestIds.clear()
        }

        /**
         * Returns a unique flow id [Long].
         */
        private var nextId: FlowId = 0
        private val nextIdLock = Mutex()

//            get() {
//                if (field == FlowId.MAX_VALUE)
//                    throw RuntimeException("flow id reached its maximum value")
//                field++
//                return field - 1
//            }
//            private set

        suspend fun nextId(): FlowId =
            nextIdLock.withLock {
                if (nextId == FlowId.MAX_VALUE) throw RuntimeException("flow id reached its maximum value")
                nextId++
                nextId - 1
            }

        /**
         * Stores the [NodeId] of the destination for each [NetFlow]
         * not yet discarded by the garbage collector.
         */
        internal val flowsDestIds: Map<FlowId, NodeId> get() = _flowsDestIds
        private val _flowsDestIds = mutableMapOf<FlowId, NodeId>()
    }
}

/**
 * Type alias for improved understandability.
 */
public typealias FlowId = Long
