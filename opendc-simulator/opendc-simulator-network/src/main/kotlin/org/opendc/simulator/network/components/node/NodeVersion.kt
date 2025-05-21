package org.opendc.simulator.network.components.node

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser

@Serializable
internal sealed interface NodeVersion {

    val rxUpdateDisp: FWDispenser<Node.RxUpdt>
    val applyRoutingDisp: FWDispenser<Node.ApplyRouting>
    val connectDisp: FWDispenser<Node.Connect>
    val disconnectDisp: FWDispenser<Node.Disconnect>
    val acceptConnectionDisp: FWDispenser<Node.AcceptConnection>
    val routTblUpdtDisp: FWDispenser<Node.RoutTblUpdt>
    val shareRoutVectDisp: FWDispenser<Node.ShareRoutVect>

    context(NetSimScope)
    suspend fun initDispensers()
}
