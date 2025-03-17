package org.opendc.simulator.network.utils.notifiable.publics

import org.opendc.simulator.network.flow.neww.publics.NetFlow

public fun interface Notification<in T: Notifiable<in T>> {
    context(T) public suspend fun handle()
}
