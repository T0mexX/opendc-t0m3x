package org.opendc.simulator.network.utils.evntemitter

import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FW

internal interface IEvnt<T: EvntEmitter<T>, Self> : Evnt<T, Self>, IFW<Self> where Self: Evnt<T, Self>, Self: FW<Self> {
    var nCollectors: Int

    suspend fun awaitHandling(): Self

    suspend fun emit(from: T, dispose: Boolean = true): Self



    enum class State {
        HANDLED,
        PENDING,
        UNTRACKED
    }
}
