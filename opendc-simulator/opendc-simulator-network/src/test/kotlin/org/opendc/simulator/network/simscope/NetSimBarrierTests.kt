package org.opendc.simulator.network.simscope

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityException
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

class NetSimBarrierTests : FunSpec({

    suspend fun testStabilityEnforcing(nValidators: Int, nStabilizers: Int, valWithBarrier: Boolean = true) = coroutineScope {
        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
        val stabilizers = 0.rangeTo(nStabilizers).map { barrier.stabilizer() }
        val validators by lazy { 0.rangeTo(nValidators).map { runBlocking { barrier.stabilizer() } } }
        val invalidated = MutableStateFlow(false)
        val invalidate = MutableStateFlow(false)
        coroutineScope {
            if (valWithBarrier) {
                repeat(nValidators) { launch {
                    barrier.whileStable(NetSimStabilityMode.ENFORCED) {
                        invalidate.emit(true)
                        repeat(10) {
                            invalidated.first() shouldBeEqual false
                            delay(100)
                        }
                    }
                } }
            } else {
                validators.map { stab -> launch {
                    stab.whileNetStable(NetSimStabilityMode.ENFORCED) {
                        invalidate.emit(true)
                        repeat(10) {
                            invalidated.first() shouldBeEqual false
                            delay(100)
                        }
                    }
                } }
            }
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
        if (valWithBarrier) {
            barrier.awaitStability()
        } else {
            validators.map { stab -> launch { stab.awaitStability() } }
        }
    }

    suspend fun testStabilityChecking(nValidators: Int, nStabilizers: Int, valWithBarrier: Boolean = true) = coroutineScope {
        val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
        val stabilizers = 0.rangeTo(nStabilizers).map { barrier.stabilizer() }
        val validators by lazy { 0.rangeTo(nValidators).map { runBlocking { barrier.stabilizer() } } }
        shouldNotThrowAny {
            if (valWithBarrier) {
                repeat(nValidators) { launch {
                    barrier.whileStable(NetSimStabilityMode.CHECKED) {
                        delay(100)
                    }
                } }
            } else {
                validators.map { stab -> launch {
                    stab.whileNetStable(NetSimStabilityMode.CHECKED) {
                        delay(100)
                    }
                } }
            }
        }
        shouldThrow<NetSimStabilityException> {
            val invalidate = MutableStateFlow(false)
            if (valWithBarrier) {
                repeat(nValidators) { launch {
                    barrier.whileStable(NetSimStabilityMode.CHECKED) {
                        invalidate.emit(true)
                        delay(100)
                    }
                } }
            } else {
                validators.map { stab -> launch {
                    stab.whileNetStable(NetSimStabilityMode.CHECKED) {
                        invalidate.emit(true)
                        delay(100)
                    }
                } }
            }
            invalidate.first { it }
            stabilizers.random().invalidate()
        }
    }

    suspend fun testStabilityAssumed(nStabilizers: Int) = coroutineScope {
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
            context("many stability validators") {
                test("stability enforcing").config(timeout = 10000.milliseconds) {
                    checkAll(iterations = 5, Arb.int(1, 50)) { nValidators ->
                        testStabilityEnforcing(nStabilizers = 1, nValidators = nValidators)
                    }
                }

                test("stability checking").config(timeout = 10000.milliseconds) {
                    checkAll(iterations = 5, Arb.int(1, 50)) { nValidators ->
                        testStabilityChecking(nValidators = nValidators, nStabilizers = 1)
                    }
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
            test("stability enforcing").config(timeout = 10000.milliseconds) {
                checkAll(iterations = 5, Arb.int(1, 50), Arb.int(1, 50)) { nValidators, nStabilizers ->
                    testStabilityEnforcing(nStabilizers = nStabilizers, nValidators = nValidators)
                }
            }

            test("stability checking").config(timeout = 10000.milliseconds) {
                checkAll(iterations = 5, Arb.int(1, 50), Arb.int(1, 50)) { nValidators, nStabilizers ->
                    testStabilityChecking(nValidators = nValidators, nStabilizers = nStabilizers)
                }
            }

            test("stability assumed").config(timeout = 5000.milliseconds) {
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
            test("stability assumed").config(timeout = 50000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000)) { nStabilizers ->
                    testStabilityAssumed(nStabilizers = nStabilizers)
                }
            }
        }
        context("many stability validators") {
            test("stability enforcing").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000), Arb.int(1, 50)) { nStabilizers, nValidators ->
                    testStabilityEnforcing(nValidators = nValidators, nStabilizers = nStabilizers)
                }
            }

            test("stability checking").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000), Arb.int(1, 50)) { nStabilizers, nValidators ->
                    testStabilityChecking(nValidators = nValidators, nStabilizers = nStabilizers)
                }
            }
        }
    }

    context("validate through stabilizers") {
        context("1 validator") {
            test("stability enforcing").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000)) { nStabilizers ->
                    testStabilityEnforcing(nValidators = 1, nStabilizers = nStabilizers, valWithBarrier = false)
                }
            }

            test("stability checking").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000)) { nStabilizers ->
                    testStabilityChecking(nValidators = 1, nStabilizers = nStabilizers, valWithBarrier = false)
                }
            }
        }
        context("many validators") {
            test("stability enforcing").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000), Arb.int(1, 50)) { nStabilizers, nValidators ->
                    testStabilityEnforcing(nValidators = nValidators, nStabilizers = nStabilizers, valWithBarrier = false)
                }
            }

            test("stability checking").config(timeout = 500000.milliseconds) {
                checkAll(iterations = 5, Arb.int(50, 1000), Arb.int(1, 50)) { nStabilizers, nValidators ->
                    testStabilityChecking(nValidators = nValidators, nStabilizers = nStabilizers, valWithBarrier = false)
                }
            }
        }
    }

    context("while invalidated") {
        test("stabilizer").config(timeout = 10000.milliseconds) {
            val barrier = NetSimBarrier(NetSimConfig.DEFAULT)
            val stabilizers = 0.rangeTo(100).map { barrier.stabilizer() }
            val await = MutableStateFlow(false)
            stabilizers.map { stab -> launch {
                await.emit(true)
                stab.whileInvalidated {
                    delay(Arb.long(0, 100).next())
                    delay(100)
                }
            } }
            await.first { it }
            measureTimeMillis {
                barrier.awaitStability()
            } shouldBeGreaterThanOrEqual 100
        }
    }
})
