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
import org.opendc.simulator.network.utils.notifiable.MsgImpl

/**
 *
 */
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

    override suspend fun claimBw(bw: DataRate): DataRate = mtx.withLock {
        assert(bw != DataRate.zero)

        val available: DataRate = maxBw - usedBw
        return if (bw > available) {
            usedBw = maxBw
            available
        } else {
            usedBw += bw
            bw
        }
    }

    /**
     * TODO
     * context to force method to be invoked in fariness policy phase.
     */
    context(FairnessPolicy)
    override suspend fun releaseBw(bw: DataRate, netF: INetFlow) = mtx.withLock {
        assert(bw approxSmallerOrEq usedBw && bw > DataRate.zero) {"${bw.value} ${usedBw.value}"}

        if (bw.approx(DataRate.zero, epsilon = 1.0)) return@withLock

        usedBw = (usedBw - bw).roundToIfWithinEpsilon(DataRate.zero, epsilon = 1.0)
        receiverPort.owner.msgAsyncRxUpdt(-bw, netF)
    }

    override suspend fun msgAsyncRxUpdt(deltaRate: DataRate, netF: INetFlow) {
        receiverPort.owner.msgAsyncRxUpdt(deltaRate, netF)
    }
}
