package org.opendc.simulator.network.components.node

import org.opendc.simulator.network.flow.publics.NetFlow

internal interface SenderNode: Node {
    suspend fun startFlow(netflow: NetFlow)
    suspend fun stopFlow(netFlow: NetFlow)
}
