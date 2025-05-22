package org.opendc.simulator.network.components.node.internals.flowtable

import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.tracker.Trackable
import org.opendc.simulator.network.utils.tracker.TrackablePropId
import org.opendc.simulator.network.utils.tracker.Tracker

/**
 * TODO
 */
internal class NodeFlowEntry private constructor(
    override val pool: FWPool<NodeFlowEntry, FWId<NodeFlowEntry>>,
    override val poolIdx: Idx,
    val txPorts: MutableMap<Port, Percentage>,
    var portFlowEntryIds: IntArray,
): IFW<NodeFlowEntry>, Trackable<NodeFlowEntry> {
    lateinit var node: Node<*>
    lateinit var netFlow: INetFlow
    override var tracker: Tracker<NodeFlowEntry>? = null
    var rx: DataRate = DataRate.zero
        set(value) {
            assert(value >= DataRate.zero)

            tracker!!.handleFieldChange(propId = RxProp) { field = value }
        }

    context(Node<*>)
    fun resizeIfNeeded() {
        if (this@Node.nPorts > portFlowEntryIds.size) {
            portFlowEntryIds = portFlowEntryIds.copyOf(this@Node.nPorts)
        }
    }

    fun tput(): DataRate =
        txPorts.keys.sumOfUnit { p ->
            p.getTxTput(portFlowEntryIds[p.portIdx])
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
                    txPorts = mutableMapOf(),
                    portFlowEntryIds = IntArray(10) { -1 },
                )
            }

        object RxProp : TrackablePropId<NodeFlowEntry>
    }
}
