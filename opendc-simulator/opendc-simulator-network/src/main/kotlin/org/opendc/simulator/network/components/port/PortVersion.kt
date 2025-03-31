package org.opendc.simulator.network.components.port

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser

@Serializable
internal sealed interface PortVersion {
    context(NetSimScope)
    suspend operator fun invoke(owner: Node, portIdx: Idx): Port

    val startProcessingDisp: FWDispenser<Port.StartProcessing>
    val setDemandDisp: FWDispenser<Port.SetDemand>
    val connectDisp: FWDispenser<Port.Connect>
    val disconnectDisp: FWDispenser<Port.Disconnect>

    context(NetSimScope)
    suspend fun initDispensers()
}
