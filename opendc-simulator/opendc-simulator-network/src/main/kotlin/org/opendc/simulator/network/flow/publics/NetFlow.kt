package org.opendc.simulator.network.flow.publics

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.utils.evntemitter.Evnt
import org.opendc.simulator.network.utils.evntemitter.EvntEmitter
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable

public interface NetFlow: Invalidatable, EvntEmitter<NetFlow> {
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

    public interface TPutChanged: Evnt<NetFlow, TPutChanged>, FW<TPutChanged> {
        public var netFlow: NetFlow
        public var old: DataRate
        public var new: DataRate

        public companion object : FWId<TPutChanged>
    }

    public interface FragmentCompleted: Evnt<NetFlow, FragmentCompleted>, FW<FragmentCompleted> {
        public var netFlow: NetFlow

        public companion object : FWId<FragmentCompleted>
    }
//
//    public interface DemandChanged: Evnt<NetFlow>, FW<DemandChanged> {
//        public var netFlow: NetFlow
//        public var old: DataRate
//        public var new: DataRate
//
//        public companion object : FWId<DemandChanged>
//    }
}

