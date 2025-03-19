package org.opendc.simulator.network.utils.notifiable.publics

public fun interface Notification<in T: Notifiable<in T>> {
    context(T) public suspend fun handle()
}
