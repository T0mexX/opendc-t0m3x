package org.opendc.simulator.network.utils.flyweight.internals

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import kotlin.coroutines.coroutineContext


internal class FWPool<out T: FW<T>, out O: FWId<T>>(
    private val nSubPools: Int = 10,
    private val objConstructor: suspend (FWPool<T, O>, Idx) -> T
) : FWDispenser<T> {
    private var nextIdx: Int = 0
    private val nextIdxMtx = Mutex()
    private val subPools: List<Channel<T>> = 0.rangeTo(nSubPools).map { Channel(Channel.UNLIMITED) }

    // TODO remove
    private var bo = 0
    private var bo2 = 0
    private val mtx = Mutex()

    private suspend fun nextIdx(): Idx = nextIdxMtx.withLock {
        nextIdx++ % nSubPools
    }


    override suspend fun acquire(): T {
        val poolIdx = coroutineContext[CoroutineID]!!.value % nSubPools

        return subPools[poolIdx]
            .tryReceive()
            .getOrNull()?.also {
                    // TODO: remove
//                    mtx.withLock {
//                        bo2++
//                    }
                }
                ?: objConstructor(this, poolIdx)
                    // TODO: remove
                    .also {
//                        mtx.withLock {
//                            bo++
//                            println(it::class.java.interfaces.toList().toString() + " requested:$bo2, created:$bo")
//                        }
                    }
    }

//    suspend fun dispenser(): FWDispenser<T> {
//        val idx = nextIdx()
//        return FWDispenser {
//            subPools[idx]
//                .tryReceive()
//                .getOrNull()?.also {
//                    // TODO: remove
////                    mtx.withLock {
////                        bo2++
////                    }
//                }
//                ?: objConstructor(this, idx)
//                    // TODO: remove
//                    .also {
////                        mtx.withLock {
////                            bo++
////                            println(it::class.java.interfaces.toList().toString() + " requested:$bo2, created:$bo")
////                        }
//                    }
//        }
//    }

    suspend fun dispose(obj: @UnsafeVariance T) {
        subPools[(obj as IFW<*>).poolIdx].send(obj)
    }
}



