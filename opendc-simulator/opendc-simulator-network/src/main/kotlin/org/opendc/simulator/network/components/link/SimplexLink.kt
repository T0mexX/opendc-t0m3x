package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.publics.Notification

internal class SimplexLink(
    override val receiverPort: Port,
    override val maxBw: DataRate,
): SendLink, ReceiveLink, SendChannel<Notification<Node>> by receiverPort.owner.notificationChl {
    private var usedBw: DataRate = DataRate.zero
    private val mtx = Mutex()

    override suspend fun getUtil(): Percentage = mtx.withLock {
        usedBw / maxBw
    }

    override suspend fun claimBw(bw: DataRate): DataRate = mtx.withLock {
        val available: DataRate = maxBw - usedBw
        return if (bw > available) {
            usedBw = maxBw
            available
        } else {
            usedBw += bw
            bw
        }
    }

    override suspend fun releaseBw(bw: DataRate) = mtx.withLock {
        require(bw < usedBw)
        usedBw -= bw
    }
}
