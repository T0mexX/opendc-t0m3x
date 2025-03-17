@file:OptIn(InternalOdcNetworkApi::class)

package org.opendc.simulator.network.api

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.flow.FlowId2
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.InternalOdcNetworkApi
import org.opendc.simulator.network.utils.eventEmitter.Event
import org.opendc.simulator.network.utils.eventEmitter.EventEmitter
import org.opendc.simulator.network.utils.invalidatable.Invalidatable
import org.opendc.simulator.network.utils.invalidatable.InvalidatorChl
import org.opendc.simulator.network.utils.notifiable.Notifiable
import org.opendc.simulator.network.utils.notifiable.Notification

public class NetFlow private constructor(
    override val stabilizer: NetSimStabilizer,
): EventEmitter<NetFlow>, Notifiable<NetFlow>, Invalidatable {
    private val _notificationChl: Channel<Notification<NetFlow>> = InvalidatorChl(this)
    override val notificationChl: SendChannel<Notification<NetFlow>> = _notificationChl


    private val _eventFlow: MutableSharedFlow<Event<NetFlow>> = MutableSharedFlow()
    override val eventFlow: SharedFlow<Event<NetFlow>> = _eventFlow



    internal companion object {
        context(NetSimScope)
        operator suspend fun invoke(
            senderId: NodeId2,
            destinationId: NodeId2,
            id: FlowId2 =
        ): NetFlow {

        }
    }
}
