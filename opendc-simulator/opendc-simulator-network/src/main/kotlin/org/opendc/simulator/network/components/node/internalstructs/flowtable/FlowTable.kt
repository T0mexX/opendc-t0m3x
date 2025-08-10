/*
 * Copyright (c) 2025 AtLarge Research
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

package org.opendc.simulator.network.components.node.internalstructs.flowtable

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.tracker.TrackablePropId
import org.opendc.simulator.network.utils.tracker.Tracker
import org.opendc.simulator.network.utils.tracker.TrackerMode

/**
 * TODO
 */
internal interface FlowTable : Tracker<NodeFlowEntry> {
    context(NetSimScope, Node<*>)
    suspend fun rxUpdt(updt: Node.RxUpdt)

    /**
     * TODO
     */
    context(NetSimScope, Node<*>)
    suspend fun updtTputs()

    /**
     * TODO
     */
    context(NetSimScope, Node<*>)
    suspend fun reapplyRouting()

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun reset(f: NetFlow)

    /**
     * TODO
     */
    context(RoutPolicy)
    suspend fun rmEntry(e: NodeFlowEntry)

    companion object {
        object AllByDemand : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> =
                setOf(NodeFlowEntry.Companion.RxProp)

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = rx > DataRate.zero || f.srcId == n.id

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int = rx.tobps().toInt() - other.rx.tobps().toInt()
        }

        object UnsatisfiedByUnsatisfaction : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> =
                setOf(NodeFlowEntry.Companion.RxProp)

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = rx > DataRate.zero

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int =
                (tput() - rx).tobps().toInt() - (other.tput() - other.rx).tobps().toInt()
        }

        object AllByThroughput : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> =
                emptySet()

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = true

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int = tput().tobps().toInt() - other.tput().tobps().toInt()
        }

        object Generated : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = n.id == f.srcId

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int = 0
        }

        object Consumed : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = n.id == f.destId

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int = 0
        }

        object Outgoing : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = n.id != f.destId

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int = 0
        }

        object Incoming : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()

            override fun NodeFlowEntry.shouldBeTracked(): Boolean = n.id != f.srcId

            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int = 0
        }
    }
}
