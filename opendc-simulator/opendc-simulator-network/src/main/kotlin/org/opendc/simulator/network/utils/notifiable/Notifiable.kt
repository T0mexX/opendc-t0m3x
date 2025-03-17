package org.opendc.simulator.network.utils.notifiable

import kotlinx.coroutines.channels.SendChannel

public interface Notifiable<T: Notifiable<T>> {
    val notificationChl: SendChannel<Notification<T>>
}
