package org.opendc.simulator.network.utils.notifiable

import kotlinx.coroutines.channels.SendChannel
import org.opendc.simulator.network.api.NetFlow

public interface Notifiable<T: Notifiable<T>> {
    public val notificationChl: SendChannel<Notification<NetFlow>>
}
