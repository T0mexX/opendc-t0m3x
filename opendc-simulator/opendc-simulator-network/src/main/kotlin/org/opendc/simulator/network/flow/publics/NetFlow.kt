package org.opendc.simulator.network.flow.publics

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.utils.`eventEmitter-old`.publics.Event
import org.opendc.simulator.network.utils.`eventEmitter-old`.publics.EventEmitter
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification

public interface NetFlow: EventEmitter<NetFlow>, Notifiable<NetFlow>, Invalidatable {
    public val id: FlowId
    public val senderId: NodeId
    public val destId: NodeId
    public val demand: DataRate
    public val throughput: DataRate

    public suspend fun setDemand(demand: DataRate)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public interface ThroughputChanged: Event<NetFlow>, FW<ThroughputChanged> {
        public var netFlow: NetFlow
        public var old: DataRate
        public var new: DataRate

        public companion object : FWId<ThroughputChanged>
    }

    public interface FragmentCompleted: Event<NetFlow>, FW<FragmentCompleted> {
        public var netFlow: NetFlow

        public companion object : FWId<FragmentCompleted>
    }

    public interface DemandChanged: Event<NetFlow>, FW<DemandChanged> {
        public var netFlow: NetFlow
        public var old: DataRate
        public var new: DataRate

        public companion object : FWId<DemandChanged>
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public interface SetDemand: Notification<NetFlow>, FW<SetDemand> {
        public var newDemand: DataRate

        public companion object : FWId<SetDemand>
    }
}

