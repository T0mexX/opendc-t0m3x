package org.opendc.simulator.network.sync.prioritizedgroupmtx


//internal class PrioritizedGroupMtx1(
//    override val prioritizedGroupId: Any = HIGH_PRIORITY,
//    override val otherGroupId: Any = LOW_PRIORITY,
//) : PrioritizedGroupMtx() {
//    init {
//        require(prioritizedGroupId != otherGroupId)
//    }
//
////    private val aWants = MutableStateFlow(false)
//    private val count = MutableStateFlow(0)
//
//
//    override suspend fun <T> withPrioritizedLock(block: suspend () -> T): T {
////        aWants.value = true
//        count.first {
//            it <= 0
//                && count.updateAndGet { count ->
//                    if (count <= 0) count - 1
//                    else count
//                } < 0
//        }
//        val result: T = block()
//        count.getAndUpdate {
//            val newCount = it + 1
////            if (newCount == 0) aWants.compareAndSet(expect = true, update = false)
//            newCount
//        }
//        return result
//    }
//
//    override suspend fun <T> withNonPrioritizedLock(block: suspend () -> T): T {
//        count.first {
//            it >= 0
////                && !aWants.value
//                && count.updateAndGet { count ->
//                    if (count < 0) count
//                    else count + 1
//                } > 0
//        }
//        val result = block()
//        count.getAndUpdate { it - 1 }
//
//        return result
//    }
//}
