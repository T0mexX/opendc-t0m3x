package org.opendc.simulator.network.sync.prioritizedgroupmtx


internal interface FlyWeightPool<T: FlyWeight<T>> {
    val nSubPools: Int

    suspend fun acquire(poolIdx: PoolIdx): T

    suspend fun dispose(obj: T)

    suspend fun getIdx(): PoolIdx
}



