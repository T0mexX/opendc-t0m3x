package org.opendc.simulator.network.flow.publics

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.utils.evntemitter.publics.Evnt
import org.opendc.simulator.network.utils.evntemitter.publics.EvntEmitter
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable

public interface NetFlow: Invalidatable {
    public val id: FlowId
    public val senderId: NodeId
    public val destId: NodeId
    public val demand: DataRate
    public val throughput: DataRate

    public suspend fun setDemand(demand: DataRate)

    /**
     * Hashing based on [id].
     */
    override fun hashCode(): Int

    /**
     * Equality based on [id].
     */
    override fun equals(other: Any?): Boolean

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//    public interface ThroughputChanged: Evnt<NetFlow>, FW<ThroughputChanged> {
//        public var netFlow: NetFlow
//        public var old: DataRate
//        public var new: DataRate
//
//        public companion object : FWId<ThroughputChanged>
//    }
//
//    public interface FragmentCompleted: Evnt<NetFlow>, FW<FragmentCompleted> {
//        public var netFlow: NetFlow
//
//        public companion object : FWId<FragmentCompleted>
//    }
//
//    public interface DemandChanged: Evnt<NetFlow>, FW<DemandChanged> {
//        public var netFlow: NetFlow
//        public var old: DataRate
//        public var new: DataRate
//
//        public companion object : FWId<DemandChanged>
//    }
}

