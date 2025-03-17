package org.opendc.simulator.network.utils.notifiable.publics

import kotlinx.coroutines.channels.SendChannel

public interface Notifiable<T: Notifiable<T>> {
    public val notificationChl: SendChannel<Notification<T>>
}
