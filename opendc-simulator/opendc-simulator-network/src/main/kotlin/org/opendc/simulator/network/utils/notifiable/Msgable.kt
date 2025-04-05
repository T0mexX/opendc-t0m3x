package org.opendc.simulator.network.utils.notifiable

import kotlinx.coroutines.channels.SendChannel

/**
 * TODO
 */
internal interface Msgable<T: Msgable<T>> {
    /**
     * TODO
     */
    val msgChl: SendChannel<Msg<T, *>>

    /**
     * TODO
     */
    val priorityMsgChl: SendChannel<Msg<T, *>>
}
