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
import org.opendc.common.units.Percentage
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.fwpool.FWDispenser
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.simscope.fwpool.FWPool
import org.opendc.simulator.network.simscope.fwpool.IFW
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.tracker.Trackable
import org.opendc.simulator.network.utils.tracker.TrackablePropId
import org.opendc.simulator.network.utils.tracker.Tracker

/**
 * TODO
 */
internal class NodeFlowEntry private constructor(
    override val pool: FWPool<NodeFlowEntry, FWId<NodeFlowEntry>>,
    override val poolIdx: Idx,
    val txlinks: MutableMap<Link, Percentage>,
    var linkFlowEntryIds: IntArray,
) : IFW<NodeFlowEntry>, Trackable<NodeFlowEntry> {
    lateinit var n: Node<*>
    lateinit var f: INetFlow

    override var tracker: Tracker<NodeFlowEntry>? = null
    var rx: DataRate = DataRate.zero
        set(value) {
            assert(value >= DataRate.zero)

            tracker!!.handleFieldChange(propId = RxProp) { field = value }
        }

    context(Node<*>)
    fun resizeIfNeeded() {
        if (this@Node.nPorts > linkFlowEntryIds.size) {
            linkFlowEntryIds = linkFlowEntryIds.copyOf(this@Node.nPorts)
        }
    }

    fun tput(): DataRate =
        txlinks.keys.sumOfUnit { l ->
            l.getTx(linkFlowEntryIds[l.linkIdx])
        }.also {
            assert(it >= DataRate.zero)
        }

    companion object : FWId<NodeFlowEntry> {
        // TODO setup dispenser beforehand

        context(NetSimScope)
        suspend fun dispenser(): FWDispenser<NodeFlowEntry> =
            poolAggr.getOrAdd(Companion as FWId<NodeFlowEntry>) { pool, idx ->
                NodeFlowEntry(
                    pool = pool,
                    poolIdx = idx,
                    txlinks = mutableMapOf(),
                    linkFlowEntryIds = IntArray(10) { -1 },
                )
            }

        object RxProp : TrackablePropId<NodeFlowEntry>
    }
}
