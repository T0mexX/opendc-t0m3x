package org.opendc.simulator.network.sync.prioritizedgroupmtx

//internal suspend fun main() {
////    println(test(PrioritizedGroupMtx1()))
//    println(test(PrioritizedGroupMtx2()))
//    println(test(PrioritizedGroupMtx3()))
//}
//
//
//private suspend fun test(mtx: PrioritizedGroupMtx): Long =
//    measureNanoTime {
//        coroutineScope {
////            repeat(10) {
//                launch {
//                    repeat(1000) {
//                        mtx.withLockAs(PrioritizedGroupMtx.HIGH_PRIORITY) {
//                            delay(0)
//                        }
//                    }
//                }
////                launch {
////                    repeat(100) {
////                        mtx.withLockAs(PrioritizedGroupMtx.LOW_PRIORITY) {
////                            delay(10)
////                        }
////                    }
////                }
////            }
//        }
//    }
