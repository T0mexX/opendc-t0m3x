package org.opendc.simulator.network.components.node.internals.flowtable

import kotlinx.coroutines.flow.asFlow
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
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

    /**
     * TODO
     */
    context(NetSimScope, Node<*>)
    override suspend fun rxUpdt(updt: Node.RxUpdt) {
        if (updt.netF.senderNode !== this@Node && updt.deltaRate == DataRate.zero) return

        val entry = _flows.getOrPut(updt.netF) {
            newEntry(updt)
        }

        //
        // Assert the node is not used both in the path to the intermediate and the final node
        // (routing protocol should ensure this).
        val f = updt.netF
        assert(f.intermediate == null || f.intermediate!! == this@Node.id || updt.toIntermediate == entry.toIntermediate)

        entry.rx = (entry.rx + updt.deltaRate).roundToIfWithinEpsilon(DataRate.zero, epsilon = 1.0)
        assert(entry.rx approxSmallerOrEq (f.demand.takeIf { f.parentFlow == null } ?: f.parentFlow!!.demand))
        entry.node = this@Node
        if (f.destId == this@Node.id) {
            if (f.parentFlow == null) f.msgAsyncSetTput(entry.rx)
            else f.parentFlow!!.msgAsyncIncreaseTputBy(updt.deltaRate)

            return
        }

        assert(entry.rx >= DataRate.zero) { entry.rx.value }

//        if (entry.rx == DataRate.zero) return

        entry.txPorts.forEach { (p, perc) ->
            // The data-rate sent to port `p` of this flow.
            val portDemand = entry.rx * perc
            entry.portFlowEntryIds[p.portIdx] =
                p.msgSetTxDemand(portDemand, entry.netFlow, entry.portFlowEntryIds[p.portIdx].takeUnless { it == -1 })!!
        }
        if (entry.netFlow.senderNode !== this@Node &&  entry.rx approx DataRate.zero) rmEntry(entry)
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

    override fun get(f: NetFlow): NodeFlowEntry = _flows[f]!!

    context(NetSimScope, Node<*>)
    private suspend fun newEntry(updt: Node.RxUpdt): NodeFlowEntry {
        // Acquire and initialize the new `NodeFlowEntry` flyweight object.
        val entry = nodeFlowEntryDispenser.acquire {
            tracker = this@FlowTableV1
            resizeIfNeeded()
            netFlow = updt.netF
            node = this@Node
            rx = DataRate.zero
            txPorts.clear()
            // If this is the intermediate node, then switch `toIntermediate` boolean to `false`.
            toIntermediate = updt.toIntermediate && updt.netF.intermediate != this@Node.id
        }

        // Apply routing policy.
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
