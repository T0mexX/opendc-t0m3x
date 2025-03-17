package org.opendc.simulator.network.simscope

import org.opendc.simulator.network.utils.flyweight.FlyWeight
import org.opendc.simulator.network.utils.flyweight.FlyWeightPool
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

internal class NetSimPoolAggregator(
    private val nSubPools: Int,
): AbstractCoroutineContextElement(Key) {

    private val poolsByType = mutableMapOf<KClass<*>, FlyWeightPool<*>>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: FlyWeight<T>> getPool(
        noinline objConstructor: () -> T
    ): FlyWeightPool<T> =
        poolsByType.getOrPut(T::class) {
            FlyWeightPool(
                nSubPools = nSubPools,
                objConstructor = objConstructor
            )
        } as FlyWeightPool<T>

    companion object Key : CoroutineContext.Key<NetSimPoolAggregator> {
        context(NetSimScope)
        operator fun invoke(): NetSimPoolAggregator =
            NetSimPoolAggregator(
                nSubPools = devConfig.nSubPools
            )
    }
}
