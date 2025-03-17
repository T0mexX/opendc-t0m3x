package org.opendc.simulator.network.api

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.flow.FlowId2
import org.opendc.simulator.network.utils.eventEmitter.Event
import org.opendc.simulator.network.utils.eventEmitter.EventEmitter
import org.opendc.simulator.network.utils.flyweight.FlyWeight
import org.opendc.simulator.network.utils.notifiable.Notifiable

public interface NetFlow: EventEmitter<NetFlow>, Notifiable<NetFlow> {
    public val id: FlowId2
    public val senderId: NodeId2
    public val destId: NodeId2
    public val demand: DataRate
    public val throughput: DataRate

    public suspend fun setDemand(demand: DataRate)

    public interface ThroughPutChangedEvent: Event<NetFlow>, FlyWeight<ThroughPutChangedEvent> {
        public val netFlow: NetFlow
        public val old: DataRate
        public val new: DataRate
    }

    public interface FragmentCompleted: Event<NetFlow>, FlyWeight<FragmentCompleted> {
        public val netFlow: NetFlow
    }

    public interface DemandChanged: Event<NetFlow>, FlyWeight<DemandChanged> {
        public val netFlow: NetFlow
        public val old: DataRate
        public val new: DataRate
    }
}
