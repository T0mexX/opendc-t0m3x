package org.opendc.simulator.network.utils.notifiable

internal fun interface Notification<in T: Notifiable<in T>> {
//    context(T)
    suspend fun T.handle()
}
