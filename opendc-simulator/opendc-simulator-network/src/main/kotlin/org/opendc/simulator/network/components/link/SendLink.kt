package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.MsgImpl

/**
 * TODO
 */
internal interface SendLink: SendChannel<Msg<Node<*>, *>> {
    /**
     * TODO
     */
    val receiverPort: Port

    /**
     * TODO
     */
    override suspend fun send(element: Msg<Node<*>, *>)

    /**
     * TODO
     */
    val maxBw: DataRate

    /**
     * TODO
     */
    val availableBw: DataRate

    /**
     * TODO
     */
    val util: Percentage

    /**
     * TODO
     */
    suspend fun claimBw(bw: DataRate): DataRate

    /**
     * TODO
     */
    suspend fun releaseBw(bw: DataRate, netF: INetFlow)

    /**
     * TODO
     */
    suspend fun msgAsyncRxUpdt(deltaRate: DataRate, netF: INetFlow)
}
