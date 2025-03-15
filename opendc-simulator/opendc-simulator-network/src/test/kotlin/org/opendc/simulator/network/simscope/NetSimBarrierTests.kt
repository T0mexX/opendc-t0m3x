package org.opendc.simulator.network.simscope

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.kotest.property.forAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityException
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import kotlin.time.Duration.Companion.milliseconds

class NetSimBarrierTests : FunSpec({

    suspend fun testStabilityEnforcing(nValidators: Int, nStabilizers: Int) = coroutineScope {
        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
        val stabilizers = 0.rangeTo(nStabilizers).map { barrier.stabilizer() }
        val invalidated = MutableStateFlow(false)
        val invalidate = MutableStateFlow(false)
        coroutineScope {
            repeat(nValidators) { launch {
                barrier.whileStable(NetSimStabilityMode.ENFORCED) {
                    invalidate.emit(true)
                    repeat(10) {
                        invalidated.first() shouldBeEqual false
                        delay(100)
                    }
                }
            } }
            stabilizers.map {
                launch {
                    invalidate.first { it }
                    it.invalidate()
                    invalidated.emit(true)
                }
            }
        }
        stabilizers.map {
            launch {
                it.validate()
            }
        }
        barrier.awaitStability()
    }

    suspend fun testStabilityChecking(nValidators: Int, nStabilizers: Int) = coroutineScope {
        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
        val stabilizers = 0.rangeTo(nStabilizers).map { barrier.stabilizer() }
        shouldNotThrowAny {
            repeat(nValidators) { launch {
                barrier.whileStable(NetSimStabilityMode.CHECKED) {
                    delay(100)
                }
            } }
        }
        shouldThrow<NetSimStabilityException> {
            val invalidate = MutableStateFlow(false)
            launch {
                barrier.whileStable(NetSimStabilityMode.CHECKED) {
                    invalidate.emit(true)
                    delay(100)
                }
            }
            invalidate.first { it }
            stabilizers.random().invalidate()
        }
    }

    suspend fun testStabilityAssumed(nValidators: Int = 0, nStabilizers: Int) = coroutineScope {
        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
        val stabilizers = 0.rangeTo(nStabilizers).map { barrier.stabilizer() }
        shouldNotThrowAny {
            val arbLong = Arb.long(0, 200)
            stabilizers.map { stab -> launch {
                delay(arbLong.next())
                stab.invalidate()
                delay(arbLong.next())
                stab.validate()
                delay(arbLong.next())
                stab.invalidate()
                delay(arbLong.next())
                stab.validate()
            } }
            barrier.awaitStability()
        }
    }

    context("basic (no child barriers)") {
       context("1 stabilizer") {
           context("1 stability validator") {
               test("simple invalidation").config(timeout = 5000.milliseconds) {
                   forAll(iterations = 5, Arb.int(1, 20)) { nWaiters ->
                       val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
                       val stabilizer = barrier.stabilizer()
                       stabilizer.invalidate()
                       var b = false
                       launch {
                           delay(300)
                           b = true
                           stabilizer.validate()
                       }
                       repeat(nWaiters) {
                           launch {
                               barrier.awaitStability()
                               b shouldBeEqual true
                           }
                       }
                       true
                   }
               }

               test("stability enforcing").config(timeout = 10000.milliseconds) {
                   testStabilityEnforcing(nStabilizers = 1, nValidators = 1)
               }

               test("stability checking").config(timeout = 10000.milliseconds) {
                   testStabilityChecking(nValidators = 1, nStabilizers = 1)
               }

               test("stability assumed").config(timeout = 10000.milliseconds) {
                   testStabilityAssumed(nStabilizers = 1)
               }
           }
       }

        context("many stabilizers") {
            context("invalidate together") {
                test("validate gradually").config(timeout = 50000.milliseconds) {
                    forAll(iterations = 5, Arb.int(1, 50)) { nStabilizers ->
                        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
                        val stabilizers = buildList {
                            repeat(nStabilizers) { add(barrier.stabilizer()) }
                        }
                        var b = false
                        stabilizers.map { launch { it.invalidate() } }
                        launch {
                            repeat(nStabilizers) {
                                delay(100)
                                b shouldBe false
                            }
                        }
                        launch {
                            repeat(nStabilizers) { idx ->
                                delay(110)
                                stabilizers[idx].validate()
                            }
                            b = true
                        }
                        barrier.awaitStability()
                        true
                    }
                }

                test("validate together").config(timeout = 5000.milliseconds) {
                    forAll(iterations = 5, Arb.int(1, 50)) { nStabilizers ->
                        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
                        val stabilizers = buildList {
                            repeat(nStabilizers) { add(barrier.stabilizer()) }
                        }
                        stabilizers.map { launch { it.invalidate() } }
                        launch { barrier.awaitStability() }
                        stabilizers.map { launch { it.validate() } }
                        true
                    }
                }
            }

            test("no stability validation").config(timeout = 5000.milliseconds) {
                checkAll(iterations = 5, Arb.int(1, 49)) { nStabilizers ->
                    testStabilityAssumed(nStabilizers = nStabilizers)
                }
            }
        }
    }

    context("advanced (multiple child barriers)") {
        context("1 stability validator") {
            test("stability enforcing").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000)) { nStabilizers ->
                    testStabilityEnforcing(nValidators = 1, nStabilizers = nStabilizers)
                }
            }

            test("stability checking").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000)) { nStabilizers ->
                    testStabilityChecking(nValidators = 1, nStabilizers = nStabilizers)
                }
            }
        }
        test("no stability validation").config(timeout = 50000.milliseconds) {
            checkAll(iterations = 5, Arb.int(50, 1000)) { nStabilizers ->
                testStabilityAssumed(nStabilizers = nStabilizers)
            }
        }
    }
})
