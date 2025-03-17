package org.opendc.simulator.network.components.port

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId

@Serializable
internal sealed interface PortVersion {
    context(NetSimScope)
    suspend operator fun invoke(): Port

    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Port.StartProcessing>): FWDispenser<Port.StartProcessing>

    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Port.SetDemand>): FWDispenser<Port.SetDemand>
}
