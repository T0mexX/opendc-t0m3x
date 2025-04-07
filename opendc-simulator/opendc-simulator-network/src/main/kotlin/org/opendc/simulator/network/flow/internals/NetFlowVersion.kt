package org.opendc.simulator.network.flow.internals

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser

@Serializable
internal sealed interface NetFlowVersion {

    context(NetSimScope)
    suspend operator fun invoke(
        senderId: NodeId,
        destId: NodeId,
        id: FlowId? = null,
        demand: DataRate = DataRate.zero,
    ): INetFlow


    val setDemandDisp: FWDispenser<INetFlow.SetDemand>

    val demandChangedDisp: FWDispenser<NetFlow.DemandChanged>

    val throughputChangedDisp: FWDispenser<NetFlow.ThroughputChanged>

    val fragmentCompletedDisp: FWDispenser<NetFlow.FragmentCompleted>

    val setTputDisp: FWDispenser<INetFlow.SetThroughput>

    val increaseTputDisp: FWDispenser<INetFlow.IncreaseThroughput>

    context(NetSimScope)
    suspend fun initDispensers()
}
