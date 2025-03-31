package org.opendc.simulator.network.flow.internals

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId

@Serializable
internal sealed interface NetFlowVersion {

    context(NetSimScope)
    suspend operator fun invoke(
        senderId: NodeId,
        destId: NodeId,
        id: FlowId? = null,
        demand: DataRate = DataRate.zero,
    ): NetFlow

    context(NetSimScope) suspend fun dispenser(
        id: FWId<NetFlow.DemandChanged>
    ): FWDispenser<NetFlow.DemandChanged>

    context(NetSimScope) suspend fun dispenser(
        id: FWId<NetFlow.FragmentCompleted>
    ): FWDispenser<NetFlow.FragmentCompleted>

    context(NetSimScope)suspend fun dispenser(
        id: FWId<NetFlow.ThroughputChanged>
    ): FWDispenser<NetFlow.ThroughputChanged>

    context(NetSimScope)suspend fun dispenser(
        id: FWId<NetFlow.SetDemand>
    ): FWDispenser<NetFlow.SetDemand>

    context(NetSimScope)suspend fun dispenser(
        id: FWId<INetFlow.SetThroughput>
    ): FWDispenser<INetFlow.SetThroughput>

    context(NetSimScope)suspend fun dispenser(
        id: FWId<INetFlow.IncreaseThroughput>
    ): FWDispenser<INetFlow.IncreaseThroughput>
}
