package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.MsgImpl

internal interface SendLink: SendChannel<Msg<Node, *>> {
    val receiverPort: Port
    override suspend fun send(element: Msg<Node, *>)
    val maxBw: DataRate
    val availableBw: DataRate
    val util: Percentage
    suspend fun claimBw(bw: DataRate): DataRate
    suspend fun releaseBw(bw: DataRate)
}
