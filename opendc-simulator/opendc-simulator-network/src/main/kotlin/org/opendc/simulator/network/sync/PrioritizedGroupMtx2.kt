package org.opendc.simulator.network.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//internal class PrioritizedGroupMtx2(
//    override val prioritizedGroupId: Any = HIGH_PRIORITY,
//    override val otherGroupId: Any = LOW_PRIORITY,
//): PrioritizedGroupMtx() {
//
//    var priorBlocked: Boolean = false
//    val priorMtx = Mutex()
//    val priorMtxMtx = Mutex()
//    val otherMtx = Mutex()
//    var count = 0
//    val countMtx = Mutex()
//
//    override suspend fun <T> withNonPrioritizedLock(block: suspend () -> T): T {
//        priorMtx.withLock {
//            countMtx.withLock {
//                if (count == 0) otherMtx.lock()
//                count++
//            }
//        }
//
//        val res = block()
//
//        countMtx.withLock {
//            if (count == 1) otherMtx.unlock()
//            count--
//        }
//
//        return res
//    }
//
//    override suspend fun <T> withPrioritizedLock(block: suspend () -> T): T {
//        priorMtxMtx.withLock {
//            if (!priorBlocked) {
//                priorMtx.lock()
//                priorBlocked = true
//            }
//        }
//        otherMtx.withLock {
//            countMtx.withLock {
//                count--
//            }
//        }
//
//        val res = block()
//
//        countMtx.withLock {
//            if (count == -1) {
//                priorMtxMtx.withLock {
//                    priorBlocked = false
//                }
//                priorMtx.unlock()
//            }
//            count++
//        }
//
//        return res
//    }
//}
