package org.opendc.simulator.network.utils.evntemitter.publics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.evntemitter.publics.EvntCollector.Companion.invoke

/**
 * TODO
 */
internal interface IEvntEmitter<T: IEvntEmitter<T>>: EvntEmitter<T> {
    /**
     * TODO
     */
    val evntFlow: EvntFlow<T>

    context(NetSimScope) override suspend fun evntCollector(scope: CoroutineScope): EvntCollector<T> =
        EvntCollector(evntFlow = evntFlow)
}
