package org.opendc.simulator.network.utils.flyweight

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.utils.Idx


internal class FlyWeightPool<T: FlyWeight<T>>(
    private val nSubPools: Int = 10,
    private val objConstructor: suspend (Idx) -> T
) {
    private var nextIdx: Int = 0
    private val nextIdxMtx = Mutex()
    private val subPools: List<Channel<T>> = 0.rangeTo(nSubPools).map { Channel(Channel.UNLIMITED) }

    private suspend fun nextIdx(): Idx = nextIdxMtx.withLock {
        nextIdx++ % nSubPools
    }

    suspend fun dispenser(): FlyWeightDispenser<T> {
        val idx = nextIdx()
        return FlyWeightDispenser {
            subPools[idx]
                .tryReceive()
                .getOrNull()
                ?: objConstructor(idx)
        }
    }

    suspend fun dispose(obj: T) {
        subPools[(obj as IFlyWait<*>).poolIdx].send(obj)
    }
}



