package org.opendc.simulator.network.utils.notifiable

import org.opendc.simulator.network.utils.flyweight.internals.IFW

/**
 * TODO
 */
internal interface Msg<in T, Self: Msg<T, Self>>: IFW<Self>
    where T : Msgable<in T> {
    /**
     * TODO
     */
    suspend fun awaitHandling(): Self

    /**
     * TODO
     */
    context(T) suspend fun handle()

    /**
     * TODO
     */
    suspend fun sendTo(to: T, dispose: Boolean = true): Self

    /**
     * TODO
     * @param builderBlock
     */
    suspend fun reset(builderBlock: (suspend Self.() -> Unit)? = null): Self

    enum class State {
        HANDLED,
        PENDING,
        UNTRACKED
    }
}
