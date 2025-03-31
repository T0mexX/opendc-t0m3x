package org.opendc.simulator.network.components.node

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId

@Serializable
internal sealed interface NodeVersion {

    val rxUpdateDisp: FWDispenser<Node.RxUpdate>
    val reapplyRoutingDisp: FWDispenser<Node.ReapplyRouting>
    val connectDisp: FWDispenser<Node.Connect>
    val disconnectDisp: FWDispenser<Node.Disconnect>
    val acceptConnectionDisp: FWDispenser<Node.AcceptConnection>

    context(NetSimScope)
    suspend fun initDispensers()
}
