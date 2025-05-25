package org.opendc.simulator.network.simscope

import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class NetSimPoolAggregator: AbstractCoroutineContextElement(Key) {

    private val poolsById = mutableMapOf<FWId<*>, FWPool<*, *>>()

    context(NetSimScope)
    @Suppress("UNCHECKED_CAST")
    fun <T: FW<T>, O: FWId<T>> getOrAdd(
        id: O,
        objConstructor: suspend (FWPool<T, O>, Idx) -> T
    ): FWPool<T, O> =
        poolsById.getOrPut(id) {
            FWPool(objConstructor = objConstructor)
        } as FWPool<T, O>

    @Suppress("UNCHECKED_CAST")
    fun <T: FW<T>, O: FWId<T>> getPool(id: O): FWPool<T, O> =
        poolsById[id]!! as FWPool<T, O>

    companion object Key : CoroutineContext.Key<NetSimPoolAggregator>
}
