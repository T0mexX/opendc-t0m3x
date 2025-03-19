package org.opendc.simulator.network.components.port

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId

@Serializable
internal sealed interface PortVersion {
    context(NetSimScope)
    suspend operator fun invoke(owner: Node, portIdx: Idx): Port

    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Port.StartProcessing>): FWDispenser<Port.StartProcessing>

    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Port.SetDemand>): FWDispenser<Port.SetDemand>

    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Port.Connect>): FWDispenser<Port.Connect>

    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Port.Disconnect>): FWDispenser<Port.Disconnect>
}
