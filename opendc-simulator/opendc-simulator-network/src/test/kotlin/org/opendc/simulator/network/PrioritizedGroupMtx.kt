package org.opendc.simulator.network

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
//import org.opendc.simulator.network.sync.PrioritizedGroupMtx1
//import kotlin.time.Duration.Companion.milliseconds
//
//class PrioritizedGroupMtx : FunSpec({
//    context("no contention among same group") {
//        suspend fun test(nDelayed: Int, nNonDelayed: Int, groupId: Any): Boolean {
//            val groupMtx = PrioritizedGroupMtx1()
//            val arr = BooleanArray(nNonDelayed) { false }
//            repeat(nDelayed) {
//                launch {
//                    groupMtx.withLockAs(groupId) {
//                        delay(5000)
//                    }
//                }
//            }
//            repeat(nNonDelayed) { entryId ->
//                launch {
//                    groupMtx.withLockAs(groupId) {
//                        arr[entryId] = true
//                    }
//                }
//            }
//            delay(1000)
//            return arr.all { it }
//        }
//
//        test("prioritized group").config(timeout = 10000.milliseconds) {
//            forAll(
//                5,
//                Arb.int(1, 20),
//                Arb.int(1, 20),
//            ) { nDelayed, nNonDelayed ->
//                test(nDelayed, nNonDelayed, PrioritizedGroupMtx1.HIGH_PRIORITY)
//            }
//        }
//
//        test("non-prioritized group").config(timeout = 10000.milliseconds) {
//            forAll(
//                5,
//                Arb.int(1, 20),
//                Arb.int(1, 20),
//            ) { nDelayed, nNonDelayed ->
//                test(nDelayed, nNonDelayed, PrioritizedGroupMtx1.LOW_PRIORITY)
//            }
//        }
//    }
//})
