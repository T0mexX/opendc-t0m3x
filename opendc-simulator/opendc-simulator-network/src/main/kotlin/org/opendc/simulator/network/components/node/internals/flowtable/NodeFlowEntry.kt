package org.opendc.simulator.network.components.node.internals.flowtable

import org.opendc.common.units.DataRate
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeV0
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.tracker.Trackable
import org.opendc.simulator.network.utils.tracker.TrackablePropId
import org.opendc.simulator.network.utils.tracker.Tracker

internal class NodeFlowEntry private constructor(
    override val pool: FWPool<NodeFlowEntry, FWId<NodeFlowEntry>>,
    override val poolIdx: Idx,
    val txPorts: MutableSet<Port>,
    var portFlowEntryIds: IntArray,
): IFW<NodeFlowEntry>, Trackable<NodeFlowEntry> {
    lateinit var node: Node
    lateinit var netFlow: NetFlow
    override lateinit var tracker: Tracker<NodeFlowEntry>
    var rx: DataRate = DataRate.zero
        set(value) {
            tracker.handleFieldChange(propId = RxProp) { field = value }
        }

    context(Node)
    fun resizeIfNeeded() {
        if (this@Node.nPorts < portFlowEntryIds.size) {
            portFlowEntryIds = portFlowEntryIds.copyOf(this@Node.nPorts)
        }
    }

    fun tput(): DataRate =
        txPorts.sumOfUnit { p ->
            p.getTxTput(portFlowEntryIds[p.portIdx])
        }

    companion object : FWId<NodeFlowEntry> {

        // TODO setup dispenser beforehand

        context(NetSimScope)
        suspend fun dispenser(): FWDispenser<NodeFlowEntry> =
            poolAggr.getOrAdd(Companion as FWId<NodeFlowEntry>) { pool, idx ->
                NodeFlowEntry(
                    pool = pool,
                    poolIdx = idx,
                    txPorts = emptySet<Port>().toMutableSet(),
                    portFlowEntryIds = IntArray(10) { -1 },
                )
            }.dispenser()

        object RxProp : TrackablePropId<NodeFlowEntry>
    }
}
