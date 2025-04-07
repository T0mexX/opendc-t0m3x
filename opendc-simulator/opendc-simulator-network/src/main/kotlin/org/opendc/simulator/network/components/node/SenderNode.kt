package org.opendc.simulator.network.components.node

import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope

internal interface SenderNode: Node {
    context(NetSimScope)
    suspend fun startFlow(netF: INetFlow)
    context(NetSimScope)
    suspend fun stopFlow(netF: INetFlow)
}
