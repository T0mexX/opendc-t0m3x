package org.opendc.simulator.network.components.node

import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal abstract class SenderNode<Self: SenderNode<Self>>(id: NodeId): NodeImpl<Self>(id) {
    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun startFlow(netF: INetFlow) {
        // TODO: maybe check that flow does not exist
        netF.senderNode = this
        val msg = nodeVersion.rxUpdateDisp.acquire().reset()
        msg.deltaRate = netF.demand
        msg.netF = netF
        flowTable.rxUpdt(msg)
        portProcessAwait()
        msg.dispose()
    }

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun stopFlow(netF: INetFlow) {
        flowTable.reset(netF)
    }
}
