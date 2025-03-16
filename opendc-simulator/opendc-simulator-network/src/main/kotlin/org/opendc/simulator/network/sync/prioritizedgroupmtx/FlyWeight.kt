package org.opendc.simulator.network.sync.prioritizedgroupmtx

internal interface FlyWeight<T: FlyWeight<T>> {
    val flyWeightPool: FlyWeightPool<T>
    val poolIdx: PoolIdx

    @Suppress("UNCHECKED_CAST")
    suspend fun dispose() = flyWeightPool.dispose(this as T)
}
