package org.opendc.simulator.network.utils.notifiable

import kotlinx.coroutines.channels.SendChannel

internal interface Notifiable<T: Notifiable<T>> {
    val notificationChl: SendChannel<Notification<T>>
}
