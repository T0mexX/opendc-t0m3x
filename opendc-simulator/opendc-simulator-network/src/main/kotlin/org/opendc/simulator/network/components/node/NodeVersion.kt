package org.opendc.simulator.network.components.node

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId

@Serializable
internal sealed interface NodeVersion {
    context(NetSimScope)
    suspend fun dispenser(id: FlyWeightId<Node.RxUpdate>): FWDispenser<Node.RxUpdate>
    suspend fun dispenser(id: FlyWeightId<Node.Connect>): FWDispenser<Node.Connect>
    suspend fun dispenser(id: FlyWeightId<Node.Disconnect>): FWDispenser<Node.Disconnect>
    suspend fun dispenser(id: FlyWeightId<Node.ReapplyRouting>): FWDispenser<Node.ReapplyRouting>
}
