/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.simscope.fwpool

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.Idx
import kotlin.coroutines.coroutineContext

/**
 * TODO
 */
internal class FWPool<T : FW<T>, out O : FWId<T>> private constructor(
    private val fwConfig: FWConfig,
    private val objConstructor: suspend (FWPool<T, O>, Idx) -> T,
) : FWDispenser<T> {
    private val nSubPools = fwConfig.nSubPools
    private val subPoolMaxSz = fwConfig.subPoolMaxSize
    private val poolMaxSz = fwConfig.poolMaxSize
    private val subPoolMaxIdle = fwConfig.subPoolMaxIdle

    /**
     * TODO
     */
    private val subPools: List<Channel<T>> = (0..<nSubPools).map { Channel(Channel.UNLIMITED) }

    /**
     * TODO
     */
    private val subPoolSzCounters by lazy { IntArray(nSubPools) }

    /**
     * TODO
     */
    private val szCountersMtxs by lazy { (0..<nSubPools).map { Mutex() } }

    /**
     * TODO
     */
    private val subPoolIdleCounters by lazy { IntArray(nSubPools) }

    /**
     * TODO
     */
    private val idleCountersMtxs by lazy { (0..<nSubPools).map { Mutex() } }

    /**
     * TODO
     */
    private var poolSzCounter = 0

    /**
     * TODO
     */
    private val poolSzCounterMtx by lazy { Mutex() }

//    /**
//     * TODO
//     */
//    private suspend fun idleCleaner() {
//        requireNotNull(subPoolMaxIdle)
//
//        while (coroutineContext.job.isActive) {
//            // Cleanup every second.
//            delay(1000)
//
//            subPools.forEachIndexed { idx, subP ->
//                idleCountersMtxs[idx].lock()
//
//                // Number of idle objects in the pool.
//                val nIdle = subPoolIdleCounters[idx]
//                // Number of idle objects in the pool that exceeds the max allowed.
//                val exceeded = max(nIdle - subPoolMaxIdle, 0)
//
//                szCountersMtxs[idx].lock()
//                poolSzCounterMtx.lock()
//
//                // Remove up to `exceeded` objects from the pool, and let the garbage collector collect them.
//                repeat(exceeded) {
//                    subP.tryReceive().getOrNull()?.let {
//                        poolSzCounter--
//                        subPoolSzCounters[idx]--
//                        subPoolIdleCounters[idx]--
//                    }
//                }
//
//                poolSzCounterMtx.unlock()
//                szCountersMtxs[idx].unlock()
//                idleCountersMtxs[idx].unlock()
//            }
//        }
//    }

    /**
     * TODO
     */
    override suspend fun acquire(block: (suspend T.() -> Unit)?): T {
        val poolIdx = coroutineContext[CoroutineID]!!.value % nSubPools

        return subPools[poolIdx]
            .tryReceive()
            .getOrNull()
            ?.also {
                // If a maximum number of unused objects is defined, then keep track of unused number,
                // so that the cleaner coroutine can free up space.
                subPoolMaxIdle?.let {
                    idleCountersMtxs[poolIdx].withLock {
                        subPoolIdleCounters[poolIdx]--
                    }
                }
            }
            // If all objects in the pool are currently in use, then create a new one.
            ?: objConstructor(this, poolIdx)
                .also { obj ->
                    subPoolMaxSz?.let {
                        szCountersMtxs[poolIdx].withLock {
                            // If the maximum is exceeded, throw `IllegalStateException`.
                            check(subPoolSzCounters[poolIdx]++ <= subPoolMaxSz) {
                                "netSimConfig.netSimDevConfig.flyWeightConfig.subPoolMaxSz ($subPoolMaxSz) " +
                                    "exceeded for ${obj::class.java.interfaces.toList().first().simpleName}"
                            }
                        }
                    }

                    // If maximum pool size is defined.
                    poolMaxSz?.let {
                        poolSzCounterMtx.withLock {
                            // If the maximum is exceeded, throw `IllegalStateException`.
                            check(poolSzCounter++ <= poolMaxSz) {
                                "netSimConfig.netSimDevConfig.flyWeightConfig.poolMaxSz ($poolMaxSz) " +
                                    "exceeded for ${obj::class.java.interfaces.toList().first().simpleName}"
                            }
                        }
                    }

                    // Execute initializer block for the flyweight object if any.
                    block?.invoke(obj)
                }
    }

    suspend fun dispose(obj: @UnsafeVariance T) {
        obj as IFW<*>
        val idx = obj.poolIdx

        // If a maximum number of unused objects is defined.
        if (subPoolMaxIdle != null) {
            idleCountersMtxs[idx].withLock {
                // If the maximum is being exceeded, then do not refill the pool with the obj.
                if (subPoolIdleCounters[idx] >= subPoolMaxIdle) return@withLock

                subPools[(obj as IFW<*>).poolIdx].send(obj)
                subPoolIdleCounters[idx]++
            }
        } else {
            subPools[(obj as IFW<*>).poolIdx].send(obj)
        }
    }

    companion object {
        /**
         * TODO
         // TODO: make coroutine that cleans the pool when few obejcts are used.
         */
        context(NetSimScope)
        operator fun <T : FW<T>, O : FWId<T>> invoke(objConstructor: suspend (FWPool<T, O>, Idx) -> T): FWPool<T, O> =
            FWPool(
                fwConfig = this@NetSimScope.devConfig.flyWeightConfig,
                objConstructor = objConstructor,
            )
    }
}
