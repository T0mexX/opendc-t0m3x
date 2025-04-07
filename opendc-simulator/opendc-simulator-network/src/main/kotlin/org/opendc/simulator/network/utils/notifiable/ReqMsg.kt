package org.opendc.simulator.network.utils.notifiable

/**
 * TODO
 */
internal interface ReqMsg<T: Msgable<T>, A, Self: ReqMsg<T, A, Self>> : Msg<T, Self> {
    /**
     * TODO
     */
    suspend fun awaitResponse(): A
}
