package org.opendc.simulator.network.components.node.internals.flowtable

import org.opendc.common.units.DataRate
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.utils.tracker.TrackablePropId
import org.opendc.simulator.network.utils.tracker.Tracker
import org.opendc.simulator.network.utils.tracker.TrackerMode

internal interface FlowTable : Tracker<NodeFlowEntry> {
    context(Node)
    suspend fun rxUpdt(updt: Node.RxUpdate)

    context(Node)
    suspend fun reapplyRouting()

    suspend fun reset(f: NetFlow)

    companion object {

        object AllByDemand : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> =
                setOf(NodeFlowEntry.Companion.RxProp)
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = true
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  =
                rx.tobps().toInt() - other.rx.tobps().toInt()
        }

        object UnsatisfiedByUnsatisfaction : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> =
                setOf(NodeFlowEntry.Companion.RxProp)
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = rx > DataRate.zero
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  =
                (tput() - rx).tobps().toInt() - (other.tput() - other.rx).tobps().toInt()
        }

        object AllByThroughput : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> =
                emptySet()
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = true
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  =
                tput().tobps().toInt() - other.tput().tobps().toInt()
        }

        object Generated : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = node.id == netFlow.senderId
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  = 0
        }

        object Consumed : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = node.id == netFlow.destId
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  = 0
        }

        object Outgoing : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = node.id != netFlow.destId
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  = 0
        }

        object Incoming : TrackerMode<NodeFlowEntry> {
            override val trackedProps: Set<TrackablePropId<NodeFlowEntry>> = emptySet()
            override fun NodeFlowEntry.shouldBeTracked(): Boolean = node.id != netFlow.senderId
            override fun NodeFlowEntry.compare(other: NodeFlowEntry): Int  = 0
        }
    }
}
