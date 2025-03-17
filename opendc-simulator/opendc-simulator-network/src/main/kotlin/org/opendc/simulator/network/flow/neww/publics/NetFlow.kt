package org.opendc.simulator.network.flow.neww.publics

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.utils.eventEmitter.publics.Event
import org.opendc.simulator.network.utils.eventEmitter.publics.EventEmitter
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeight
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification

public interface NetFlow: EventEmitter<NetFlow>, Notifiable<NetFlow> {
    public val id: FlowId2
    public val senderId: NodeId2
    public val destId: NodeId2
    public val demand: DataRate
    public val throughput: DataRate

    public suspend fun setDemand(demand: DataRate)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public interface ThroughputChanged: Event<NetFlow>, FlyWeight<ThroughputChanged> {
        public var netFlow: NetFlow
        public var old: DataRate
        public var new: DataRate

        public companion object : FlyWeightId<ThroughputChanged>
    }

    public interface FragmentCompleted: Event<NetFlow>, FlyWeight<FragmentCompleted> {
        public var netFlow: NetFlow

        public companion object : FlyWeightId<FragmentCompleted>
    }

    public interface DemandChanged: Event<NetFlow>, FlyWeight<DemandChanged> {
        public var netFlow: NetFlow
        public var old: DataRate
        public var new: DataRate

        public companion object : FlyWeightId<DemandChanged>
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public interface SetDemand: Notification<NetFlow>, FlyWeight<SetDemand> {
        public var newDemand: DataRate

        public companion object : FlyWeightId<SetDemand>
    }
}

