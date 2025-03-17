package org.opendc.simulator.network.utils.flyweight

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal class FlyWeightPool<T: org.opendc.simulator.network.utils.flyweight.FlyWeight<T>>(
    private val nSubPools: Int = 10,
    private val objConstructor: () -> T
) {
    private var nextIdx: Int = 0
    private val nextIdxMtx = Mutex()
    private val subPools: List<Channel<T>> = 0.rangeTo(nSubPools).map { Channel(Channel.UNLIMITED) }

    private suspend fun nextIdx(): org.opendc.simulator.network.utils.flyweight.PoolIdx = nextIdxMtx.withLock {
        org.opendc.simulator.network.utils.flyweight.PoolIdx(nextIdx % nSubPools).also { nextIdx++ }
    }

    suspend fun dispenser(): org.opendc.simulator.network.utils.flyweight.FlyWeightDispenser<T> {
        val idx = nextIdx()
        return org.opendc.simulator.network.utils.flyweight.FlyWeightDispenser {
            subPools[idx.i]
                .tryReceive()
                .getOrNull()
                ?: objConstructor()
        }
    }

    suspend fun dispose(obj: T) {
        subPools[obj.poolIdx.i].send(obj)
    }
}



