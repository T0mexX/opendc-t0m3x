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
    context(Node<*>)
    override suspend fun rxUpdt(updt: Node.RxUpdate) {
        var new: Boolean = false
        val entry = _flows.getOrPut(updt.netF) {
            new = true
            newEntry(updt)
        }
        entry.rx += updt.deltaRate
        entry.node = this@Node
        if (updt.netF.destId == this@Node.id) {
            updt.netF.setThroughput(entry.rx)
            return
        }

        val perPort = entry.rx / entry.txPorts.size
        entry.txPorts.forEach {
            if (new) {
                entry.portFlowEntryIds[it.portIdx] =
                    it.msgSetTxDemand(perPort, entry.netFlow)
            } else {
                it.msgSetTxDemand(perPort, entry.netFlow, entry.portFlowEntryIds[it.portIdx])
            }
        }
        if (entry.rx approx DataRate.zero) _flows.remove(updt.netF)
    }

    /**
     * TODO
     */
    context(Node<*>) override suspend fun reapplyRouting() {
        val entriesFlow = _flows.values.asFlow()
        // Reset current port outgoing data-rates.
        entriesFlow.collect { entry ->
            entry.txPorts.forEach { p ->
                p.msgSetTxDemand(DataRate.zero, netF = entry.netFlow, entryId = entry.portFlowEntryIds[p.portIdx])
            }
        }

        // Select new outgoing ports for each flow entry.
        entriesFlow.collect { entry ->
            this@Node.routingPolicy.selectPorts(entry)
        }
    }

    /**
     * TODO
     */
    override suspend fun reset(f: NetFlow) {
        val entry = _flows[f]!!
        entry.rx = DataRate.zero
        entry.txPorts.forEach {
            entry.portFlowEntryIds[it.portIdx] =
                it.msgSetTxDemand(DataRate.zero, entry.netFlow)
        }
        _flows.remove(f)
    }

    context(Node<*>)
    private suspend fun newEntry(updt: Node.RxUpdate): NodeFlowEntry {
        val entry = nodeFlowEntryDispenser.acquire()
        entry.tracker = this
        entry.resizeIfNeeded()
        entry.node = this@Node
        entry.netFlow = updt.netF
        this@Node.routingPolicy.selectPorts(entry)
        return entry
    }

    companion object: FlowTableVersion {

        context(NetSimScope)
        override suspend fun invoke(): FlowTable = FlowTableV1(
            nodeFlowEntryDispenser = NodeFlowEntry.dispenser(),
            tracker = Tracker(),
        )
    }
}
