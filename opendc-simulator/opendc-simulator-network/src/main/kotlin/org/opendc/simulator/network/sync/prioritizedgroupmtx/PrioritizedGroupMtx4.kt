package org.opendc.simulator.network.sync.prioritizedgroupmtx

//internal class PrioritizedGroupMtx4: PrioritizedGroupMtx {
//
////    private val aLocked = object {}
////    private var aCount = 0
////    private val aMtx = Mutex()
////    private val aWaitsB = Mutex()
////
////    private val bLocked = object {}
////    private var bCount = 0
////    private val bMtx = Mutex()
////    private val bWaitsA = Mutex()
////
////    private val state = MutableStateFlow<Any?>(null)
////    val aWants = MutableStateFlow(false)
////    val aConcedes = MutableStateFlow(true)
////    val bConcedes = MutableStateFlow(true)
//
//    override suspend fun <T> withPrioritizedLock(block: suspend () -> T): T {
////        aMtx.withLock {
////            aCount++
////            aWants.value = true
////        }
////        if (!aWants.value) {
////            aWants.value = true
////        }
////        if (state.value == )
////
////
////        aMtx.withLock<Int> {
////            if (aCount > 0) {
////                aCount++
////            } else 0
////        }.let {
////            if (it > 0) return@let
////            state.com
////        }
////
////
////        state.first {
////            it == aLocked
////                || (it == null) && state.compareAndSet(expect = null, up)
////        }
//    }
//
//    override suspend fun <T> withNonPrioritizedLock(block: suspend () -> T): T {
//        TODO("Not yet implemented")
//    }
//
//    private val mtx = Mutex()
//
//    override suspend fun prioritizedLock() {
//
//    }
//
//    override suspend fun prioritizedUnlock() {
//        TODO("Not yet implemented")
//    }
//
//    override suspend fun nonPrioritizedLock() {
//        TODO("Not yet implemented")
//    }
//
//    override suspend fun nonPrioritizedUnlock() {
//        TODO("Not yet implemented")
//    }
//}
