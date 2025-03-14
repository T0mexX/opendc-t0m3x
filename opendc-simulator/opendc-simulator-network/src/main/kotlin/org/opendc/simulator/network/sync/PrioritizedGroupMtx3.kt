package org.opendc.simulator.network.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//internal class PrioritizedGroupMtx3: PrioritizedGroupMtx() {
//
//    private val aLocked = object {}
//    private val bLocked = object {}
//    private val bWaitsA = Mutex()
//    private val aWaitsB = Mutex()
//    private val state = MutableStateFlow<Any?>(null)
//    private var aCount = 0
//    private val aCountMtx = Mutex()
//    private var bCount = 0
//    private val bCountMtx = Mutex()
//
//
//    override suspend fun <T> withPrioritizedLock(block: suspend () -> T): T {
//        val success = aCountMtx.withLock {
//            if (aCount > 0) {
//                aCount++
//                return@withLock true
//            }
//            bWaitsA.lock()
//            false
//        }
//        if (success.not()) {
//            while (state.value != aLocked) {
//                aWaitsB.withLock {
//                    state.compareAndSet(expect = null, update = aLocked)
//                }
//            }
//            aCountMtx.withLock { aCount++ }
//        }
//
//        val result = block()
//
//        aCountMtx.withLock {
//            if (--aCount == 0) {
//                state.value = null
//                bWaitsA.unlock()
//            }
//        }
//
//        return result
//    }
//
//    override suspend fun <T> withNonPrioritizedLock(block: suspend () -> T): T {
//        val success = bCountMtx.withLock {
//            if (bCount > 0) {
//                bCount++
//                return@withLock true
//            }
//            false
//        }
//        if (success.not()) {
//            while (state.value != bLocked) {
//                bWaitsA.withLock {
//                    state.compareAndSet(expect = null, update = bLocked)
//                }
//            }
//            aWaitsB.lock()
//            bCountMtx.withLock { bCount++ }
//        }
//
//        val result = block()
//
//        bCountMtx.withLock {
//            if (--bCount == 0) {
//                state.value = null
//                aWaitsB.unlock()
//            }
//        }
//
//        return result
//    }
//}
