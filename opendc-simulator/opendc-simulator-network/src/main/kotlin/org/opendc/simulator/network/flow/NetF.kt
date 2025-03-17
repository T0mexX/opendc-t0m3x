@file:OptIn(InternalOdcNetworkApi::class)

package org.opendc.simulator.network.flow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.NetFlow
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.InternalOdcNetworkApi
import org.opendc.simulator.network.utils.eventEmitter.Event
import org.opendc.simulator.network.utils.invalidatable.Invalidatable
import org.opendc.simulator.network.utils.invalidatable.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.Notification

internal class NetF(
    override val senderId: NodeId2,
    override val destId: NodeId2,
    override val id: FlowId2,
    override val stabilizer: NetSimStabilizer,
    demand: DataRate,
): NetFlow, Invalidatable {

    override var throughput: DataRate = DataRate.zero
        private set
    override var demand: DataRate = demand
        private set

    private val _notificationChl: Channel<Notification<NetFlow>> = InvalidatorChl(this)
    override val notificationChl: SendChannel<Notification<NetFlow>> = _notificationChl


    private val _eventFlow: MutableSharedFlow<Event<NetFlow>> = MutableSharedFlow()
    override val eventFlow: SharedFlow<Event<NetFlow>> = _eventFlow


    internal companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            senderId: NodeId2,
            destId: NodeId2,
            id: FlowId2? = null,
            demand: DataRate = DataRate.zero,
        ): NetF = NetF(
            senderId = senderId,
            destId = destId,
            id = id ?: idDispenser.getFlowId(),
            stabilizer = barrier.stabilizer(),
            demand = demand,
        )
    }
}
