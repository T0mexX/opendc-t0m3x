package org.opendc.simulator.network.components.node.internals.flowtable

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.FlowId
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

    context(Node)
    override suspend fun sendToPorts(updt: Node.RxUpdate) {
        val entry = _flows.getOrPut(updt.netFlow) {
            newEntry(updt)
        }
        entry.rx += updt.deltaRate
        entry.node = this@Node
        val perPort = entry.rx / entry.txPorts.size
        entry.txPorts.forEach {
            entry.portFlowEntryIds[it.portIdx] =
                it.setTxDemand(perPort, entry.netFlow)
        }
        if (entry.rx approx DataRate.zero) _flows.remove(updt.netFlow)
    }

    context(Node) override suspend fun reapplyRouting() {
        TODO("Not yet implemented")
    }

    override suspend fun reset(f: NetFlow) {
        val entry = _flows[f]!!
        entry.rx = DataRate.zero
        entry.txPorts.forEach {
            entry.portFlowEntryIds[it.portIdx] =
                it.setTxDemand(DataRate.zero, entry.netFlow)
        }
        _flows.remove(f)
    }

    context(Node)
    private suspend fun newEntry(updt: Node.RxUpdate): NodeFlowEntry {
        val entry = nodeFlowEntryDispenser.acquire()
        entry.tracker = this
        entry.resizeIfNeeded()
        entry.node = this@Node
        entry.netFlow = updt.netFlow
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
