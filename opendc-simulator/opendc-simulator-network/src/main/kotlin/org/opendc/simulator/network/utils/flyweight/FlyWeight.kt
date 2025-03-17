package org.opendc.simulator.network.utils.flyweight

internal interface FlyWeight<T: FlyWeight<T>> {
    val flyWeightPool: org.opendc.simulator.network.utils.flyweight.FlyWeightPool<T>
    val poolIdx: PoolIdx

    @Suppress("UNCHECKED_CAST")
    suspend fun dispose() = flyWeightPool.dispose(this as T)

    // Override `finalize` until deprecated to throw when
    // a `FlyWeight object is GCed
}
