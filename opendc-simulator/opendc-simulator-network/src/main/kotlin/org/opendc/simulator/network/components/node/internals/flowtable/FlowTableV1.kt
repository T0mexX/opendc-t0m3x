package org.opendc.simulator.network.components.node.internals.flowtable

import kotlinx.coroutines.flow.asFlow
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.tracker.Tracker

internal class FlowTableV1 private constructor(
    val tracker: Tracker<NodeFlowEntry>,
    private val nodeFlowEntryDispenser: FWDispenser<NodeFlowEntry>,
): FlowTable, Tracker<NodeFlowEntry> by tracker {

    private val _flows = mutableMapOf<NetFlow, NodeFlowEntry>()

    init {
        tracker.itemsGetter = { _flows.values }
    }

    /**
     * TODO
     */
    context(NetSimScope, Node<*>)
    override suspend fun rxUpdt(updt: Node.RxUpdate) {
        assert(updt.deltaRate.approx(DataRate.zero).not())

        var new = false
        val entry = _flows.getOrPut(updt.netF) {
            assert(updt.deltaRate > DataRate.zero) { updt.deltaRate }
            new = true
            newEntry(updt)
        }
        entry.rx = (entry.rx + updt.deltaRate).roundToIfWithinEpsilon(DataRate.zero, epsilon = 1.0)
        entry.node = this@Node
        if (updt.netF.destId == this@Node.id) {
            updt.netF.setThroughput(entry.rx)

            return
        }

        assert(entry.rx >= DataRate.zero) { entry.rx.value }

        entry.txPorts.forEach { (p, perc) ->
            // The data-rate sent to port `p` of this flow.
            val portDemand = entry.rx * perc
            if (new) {
                entry.portFlowEntryIds[p.portIdx] =
                    p.msgSetTxDemand(portDemand, entry.netFlow) ?: -1
            } else {
                p.msgSetTxDemand(portDemand, entry.netFlow, entry.portFlowEntryIds[p.portIdx])
            }
        }
        if (entry.rx approx DataRate.zero) rmEntry(entry)
    }

    /**
     * TODO
     */
    context(NetSimScope, Node<*>) override suspend fun reapplyRouting() {
        val entriesFlow = _flows.values.asFlow()
        // Reset current port outgoing data-rates.
        entriesFlow.collect { entry ->
            entry.txPorts.keys.forEach { p ->
                p.msgSetTxDemand(DataRate.zero, netF = entry.netFlow, entryId = entry.portFlowEntryIds[p.portIdx])
            }
        }

        // Select new outgoing ports for each flow entry.
        entriesFlow.collect { entry ->
            this@Node.routPolicy.selectPorts(entry)
        }
    }

    /**
     * TODO
     */
    override suspend fun reset(f: NetFlow) {
        val entry = _flows[f]!!
        entry.rx = DataRate.zero
        entry.txPorts.keys.forEach { p ->
            entry.portFlowEntryIds[p.portIdx] =
                p.msgSetTxDemand(DataRate.zero, entry.netFlow) ?: -1
        }
        rmEntry(entry)
    }

    context(NetSimScope, Node<*>)
    private suspend fun newEntry(updt: Node.RxUpdate): NodeFlowEntry {
        val entry = nodeFlowEntryDispenser.acquire()
        entry.tracker = this
        entry.resizeIfNeeded()
        entry.netFlow = updt.netF
        entry.node = this@Node
        entry.rx = DataRate.zero
        entry.txPorts.clear()
        if (entry.netFlow.destId != this@Node.id)
            this@Node.routPolicy.selectPorts(entry)
        return entry
    }

    private suspend fun rmEntry(entry: NodeFlowEntry) {
        _flows.remove(entry.netFlow)!!.untrack().dispose()
    }

    companion object: FlowTableVersion {

        context(NetSimScope)
        override suspend fun invoke(): FlowTable = FlowTableV1(
            nodeFlowEntryDispenser = NodeFlowEntry.dispenser(),
            tracker = Tracker(),
        )
    }
}
