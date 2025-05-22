package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal abstract class SenderNode<Self: SenderNode<Self>>(ip: IPv4Address): NodeImpl<Self>(ip) {
    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun startFlow(netF: INetFlow) {
        assert(netF.demand >= DataRate.zero)
        assert(netF.destId != this.id)
        assert(netF.senderId == this.id)

        // TODO: maybe check that flow does not exist
        netF.netLaunch()
        netF.senderNode = this

        if (netF.demand.isZero()) return

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
