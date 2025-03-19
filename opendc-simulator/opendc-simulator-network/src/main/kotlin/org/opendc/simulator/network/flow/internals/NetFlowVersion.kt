package org.opendc.simulator.network.flow.internals

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.flow.publics.FlowId2
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId

@Serializable
internal sealed interface NetFlowVersion {

    context(NetSimScope)
    suspend operator fun invoke(
        senderId: NodeId,
        destId: NodeId,
        id: FlowId2? = null,
        demand: DataRate = DataRate.zero,
    ): NetFlow

    context(NetSimScope) suspend fun dispenser(
        id: FlyWeightId<NetFlow.DemandChanged>
    ): FWDispenser<NetFlow.DemandChanged>

    context(NetSimScope) suspend fun dispenser(
        id: FlyWeightId<NetFlow.FragmentCompleted>
    ): FWDispenser<NetFlow.FragmentCompleted>

    context(NetSimScope)suspend fun dispenser(
        id: FlyWeightId<NetFlow.ThroughputChanged>
    ): FWDispenser<NetFlow.ThroughputChanged>

    context(NetSimScope)suspend fun dispenser(
        id: FlyWeightId<NetFlow.SetDemand>
    ): FWDispenser<NetFlow.SetDemand>

    context(NetSimScope)suspend fun dispenser(
        id: FlyWeightId<INetFlow.SetThroughput>
    ): FWDispenser<INetFlow.SetThroughput>

    context(NetSimScope)suspend fun dispenser(
        id: FlyWeightId<INetFlow.IncreaseThroughput>
    ): FWDispenser<INetFlow.IncreaseThroughput>
}
