package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.utils.notifiable.Msg


internal class SimplexLink(
    override val receiverPort: Port,
    override val maxBw: DataRate,
): SendLink, ReceiveLink, SendChannel<Msg<Node<*>, *>> by receiverPort.owner.msgChl {
    private var usedBw: DataRate = DataRate.zero
    private val mtx = Mutex()
    override val availableBw: DataRate get() = maxBw - usedBw
    override val util: Percentage get() = usedBw / maxBw

    override suspend fun getUtil(): Percentage = mtx.withLock {
        usedBw / maxBw
    }

    context(FairnessPolicy)
    override suspend fun claimBw(bw: DataRate, netF: INetFlow): DataRate = mtx.withLock {
        assert(bw != DataRate.zero)

        // If bandwidth to be claimed is approximately 0, then do not propagate update.
        if (bw.approx(DataRate.zero, epsilon = 1.0)) return@withLock DataRate.zero

        // The currently available bandwidth on the link.
        val available: DataRate = maxBw - usedBw

        // The additional bandwidth that will be taken by `netF`.
        val deltaBw =
            if (bw > available) available
            else bw

        if (deltaBw approx DataRate.zero) return DataRate.zero

        // Update the currently used bandwidth on the link, rounding to max if necessary.
        usedBw = (usedBw + deltaBw).roundToIfWithinEpsilon(maxBw, 1.0)
//
//        // Send update to the receiver node.
//        receiverPort.owner.msgAsyncRxUpdt(deltaBw, netF)

        return deltaBw
    }

    context(FairnessPolicy)
    override suspend fun releaseBw(bw: DataRate, netF: INetFlow) = mtx.withLock {
        assert(bw approxSmallerOrEq usedBw && bw > DataRate.zero)

        // If the bandwidth to be released is approximately 0, then do not propagate update.
        if (bw.approx(DataRate.zero, epsilon = 1.0)) return@withLock

        // Update currently used bandwidth on the link, routing to 0 if necessary
        usedBw = (usedBw - bw).roundToIfWithinEpsilon(DataRate.zero, epsilon = 1.0)
//
//        // Send update to the receiver node.
//        receiverPort.owner.msgAsyncRxUpdt(-bw, netF)
    }
}
