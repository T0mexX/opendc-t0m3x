package org.opendc.simulator.network.utils.notifiable

public fun interface Notification<in T: Notifiable<in T>> {
    public suspend fun T.handle()
}
