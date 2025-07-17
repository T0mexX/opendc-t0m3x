package org.opendc.simulator.network.api.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.TimeDelta
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.random.Random
import kotlin.system.measureNanoTime

class JAdaptorsUtilsTest : FunSpec({
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Test Setup (shared by all tests in this test class)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    lateinit var rootScope: NetSimRootScope

    // Used instead of `beforeEach` because the test `coroutineContext` is needed,
    // and it is not available in `beforeEach`.
    fun TestScope.setUp() {
        rootScope = NetSimRootScope() // Add the child job to propagate exceptions.
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Network Test Logic (repeated for each test class)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    fun FunSpec.netTest(
        name: String,
        block: suspend NetSimScope.() -> Unit,
    ) = test(name) {
        setUp()
        rootScope.launch { block() }.join()
        rootScope.cancel()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    netTest("`latched` vs `runBlocking` vs `withFuture` performance") {
        val nIterations = 10000
        fun <T> NetSimScope.blackhole(value: T) {
            if (value?.hashCode() == Int.MIN_VALUE) println() // dummy use
        }

        val runBlkingNanos = measureNanoTime {
            repeat(nIterations) {
                runBlocking(rootScope.coroutineContext) {
                    with(rootScope) {
                        blackhole(Random.nextInt())
                    }
                }
            }
        }
        log.info { "`runBlocking` performance: ${TimeDelta.ofNanos(runBlkingNanos)}" }

        val netBlkingNanos = measureNanoTime {
            repeat(nIterations) {
                netBlking(rootScope) {
                    blackhole(Random.nextInt())
                }
            }
        }
        log.info { "`netBlking` performance: ${TimeDelta.ofNanos(netBlkingNanos)}" }

        val latchedNanos = measureNanoTime {
            repeat(nIterations) {
                latched(rootScope) {
                    blackhole(Random.nextInt())
                }
            }
        }
        log.info { "`latched` performance: ${TimeDelta.ofNanos(latchedNanos)} " }

        val withFutureNanos = measureNanoTime {
            repeat(nIterations) {
                withFuture(rootScope) {
                    blackhole(Random.nextInt())
                }
            }
        }
        log.info { "`withFuture` performance: ${TimeDelta.ofNanos(withFutureNanos)}" }
    }
})

