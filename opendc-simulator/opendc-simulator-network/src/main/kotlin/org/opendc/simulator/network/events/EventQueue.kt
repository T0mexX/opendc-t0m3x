package org.opendc.simulator.network.events

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

internal class EventQueue<T: WithEvents<T>> {
    private val nowChl = Channel<NowEvent<T>>()
    private val chl = Channel<Event<T>>()

    internal fun nowEvent(e: NowEvent<T>)

    suspend fun bo() {
        coroutineScope {
            val bo = async {

            }
        }
    }
}
