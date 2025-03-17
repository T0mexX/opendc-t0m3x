package org.opendc.simulator.network.simscope

import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeight
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class NetSimPoolAggregator(
    private val nSubPools: Int,
): AbstractCoroutineContextElement(Key) {

    private val poolsById = mutableMapOf<FlyWeightId<*>, FWPool<*, *>>()

    @Suppress("UNCHECKED_CAST")
    fun <T: FlyWeight<T>, O: FlyWeightId<T>> getCreatePool(
        id: O,
        objConstructor: suspend (FWPool<T, O>, Idx) -> T
    ): FWPool<T, O> =
        poolsById.getOrPut(id) {
            FWPool(
                nSubPools = nSubPools,
                objConstructor = objConstructor
            )
        } as FWPool<T, O>

    @Suppress("UNCHECKED_CAST")
    fun <T: FlyWeight<T>, O: FlyWeightId<T>> getPool(id: O): FWPool<T, O> =
        poolsById[id]!! as FWPool<T, O>

    companion object Key : CoroutineContext.Key<NetSimPoolAggregator> {
        context(NetSimScope)
        operator fun invoke(): NetSimPoolAggregator =
            NetSimPoolAggregator(
                nSubPools = devConfig.nSubPools
            )
    }
}
