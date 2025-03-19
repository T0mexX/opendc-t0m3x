package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.utils.notifiable.publics.Notification

internal interface SendLink: SendChannel<Notification<Node>> {
    val receiverPort: Port
    override suspend fun send(element: Notification<Node>)
    val maxBw: DataRate
    suspend fun getUtil(): Percentage
    suspend fun claimBw(bw: DataRate): DataRate
    suspend fun releaseBw(bw: DataRate)
}
