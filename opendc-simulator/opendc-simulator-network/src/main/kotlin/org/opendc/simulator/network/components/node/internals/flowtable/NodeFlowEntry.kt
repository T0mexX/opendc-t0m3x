package org.opendc.simulator.network.components.node.internals.flowtable

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.tracker.Trackable
import org.opendc.simulator.network.utils.tracker.TrackablePropId
import org.opendc.simulator.network.utils.tracker.Tracker

internal class NodeFlowEntry private constructor(
    override val pool: FWPool<NodeFlowEntry, FlyWeightId<NodeFlowEntry>>,
    override val poolIdx: Idx,
    var node: Node,
    val txPorts: MutableSet<Port>,
    var portFlowEntryIds: IntArray,
): IFW<NodeFlowEntry>, Trackable<NodeFlowEntry> {
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

    companion object : FlyWeightId<NodeFlowEntry> {
        context(NetSimScope, Node)
        suspend fun dispenser(): FWDispenser<NodeFlowEntry> =
            poolAggr.getOrAdd(Companion as FlyWeightId<NodeFlowEntry>) { pool, idx ->
                NodeFlowEntry(
                    pool = pool,
                    poolIdx = idx,
                    node = this@Node,
                    txPorts = emptySet<Port>().toMutableSet(),
                    portFlowEntryIds = IntArray(this@Node.nPorts) { -1 },
                )
            }.dispenser()


        object RxProp : TrackablePropId<NodeFlowEntry>
    }
}
