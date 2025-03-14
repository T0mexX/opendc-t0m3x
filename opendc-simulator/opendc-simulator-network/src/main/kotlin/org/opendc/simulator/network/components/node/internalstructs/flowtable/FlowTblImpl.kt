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

import kotlinx.coroutines.flow.asFlow
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NetCoOwner
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.simscope.FPAHandler.Companion.roundDR
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.fwpool.FWDispenser
import org.opendc.simulator.network.utils.tracker.Tracker

/**
 * Contains the information related to the [NetFlow] currently transiting,
 * being generated, or being consumed by the [Node] that owns this table.
 */
@NetCoOwner(owner = NetCo.NODE)
internal class FlowTblImpl private constructor(
    private val nodeFlowEntryDispenser: FWDispenser<NodeFlowEntry>,
    val tracker: Tracker<NodeFlowEntry>,
) : FlowTable, Tracker<NodeFlowEntry> by tracker {
    private val flows = mutableMapOf<NetFlow, NodeFlowEntry>()
    private val tputChanged = mutableSetOf<NodeFlowEntry>()

    init {
        tracker.itemsGetter = { flows.values }
    }

    /**
     * TODO
     */
    context(NetSimScope, Node<*>)
    override suspend fun rxUpdt(updt: Node.RxUpdt) {
        assert(updt.deltaRate.approx(DataRate.zero).not())

        var new = false
        val entry =
            flows.getOrPut(updt.netF) {
                assert(updt.deltaRate > DataRate.zero) { updt.deltaRate }
                new = true
                newEntry(updt)
            }
        entry.rx = (entry.rx + updt.deltaRate).roundDR(min = DataRate.zero)
        entry.node = this@Node
        if (updt.netF.destId == this@Node.id) {
            tputChanged.add(entry)

            return
        }

        assert(entry.rx >= DataRate.zero) { entry.rx.value }

        val setTempTx =
            if (new) {
                config.routPolicy.onNodeNewFReceived(entry)
            } else {
                config.routPolicy.onNodeFUpdt(updt, entry)
            }

        if (setTempTx) setTentativeTx(entry, new)
    }

    /**
     * TODO
     */
    context(NetSimScope)
    private suspend fun setTentativeTx(
        e: NodeFlowEntry,
        new: Boolean,
    ) {
        if (e.txlinks.isEmpty()) println("NOT OWRKIG")
        e.txlinks.forEach { (l, perc) ->
            // The data-rate sent to link `l` for this flow.
            val portDemand = e.rx * perc
            if (new) {
                e.linkFlowEntryIds[l.linkIdx] =
                    l.setTentativeTx(portDemand, e.netFlow)
            } else {
                e.linkFlowEntryIds[l.linkIdx] =
                    l.setTentativeTx(portDemand, e.netFlow, e.linkFlowEntryIds[l.linkIdx])
            }
        }
        if (e.rx approx DataRate.zero) rmEntry(e)
    }

    context(NetSimScope)
    override suspend fun updtTputs() {
        tputChanged.forEach { entry ->
            entry.netFlow.msgAsyncSetTput(entry.rx)
        }
        tputChanged.clear()
    }

    context(NetSimScope, Node<*>)
    override suspend fun reapplyRouting() {
        val entriesFlow = flows.values.asFlow()
        // Reset current port outgoing data-rates.
        entriesFlow.collect { entry ->
            entry.txlinks.keys.forEach { l ->
                l.setTentativeTx(DataRate.zero, f = entry.netFlow, entryId = entry.linkFlowEntryIds[l.linkIdx])
            }
        }

        // Select new outgoing ports for each flow entry.
        entriesFlow.collect { entry ->
            this@NetSimScope.routPolicy.onNodeNewFReceived(entry)
        }
    }

    /**
     * TODO
     */
    context(NetSimScope)
    override suspend fun reset(f: NetFlow) {
        val entry = flows[f] ?: return log.warn("Flow likely stopped twice")
        entry.rx = DataRate.zero
        entry.txlinks.keys.forEach { l ->
            entry.linkFlowEntryIds[l.linkIdx] =
                l.setTentativeTx(DataRate.zero, entry.netFlow) ?: -1
        }
        rmEntry(entry)
    }

    context(NetSimScope, Node<*>)
    private suspend fun newEntry(updt: Node.RxUpdt): NodeFlowEntry {
        val entry = nodeFlowEntryDispenser.acquire()
        entry.tracker = this
        entry.resizeIfNeeded()
        entry.netFlow = updt.netF
        entry.node = this@Node
        entry.rx = DataRate.zero
        entry.txlinks.clear()
        if (entry.netFlow.destId != this@Node.id) {
            this@NetSimScope.routPolicy.onNodeNewFReceived(entry)
        }
        return entry
    }

    override suspend fun rmEntry(e: NodeFlowEntry) {
        flows.remove(e.netFlow)!!.untrack().dispose()
    }

    companion object : FlowTableVersion {
        context(NetSimScope)
        override suspend fun invoke(): FlowTable =
            FlowTblImpl(
                nodeFlowEntryDispenser = NodeFlowEntry.dispenser(),
                tracker = Tracker(),
            )
    }
}
