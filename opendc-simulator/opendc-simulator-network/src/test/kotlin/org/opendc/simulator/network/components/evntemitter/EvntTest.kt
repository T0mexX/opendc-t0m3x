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

@file:OptIn(DebuggingUse::class)

package org.opendc.simulator.network.components.evntemitter

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.FunSpecContainerScope
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.opendc.common.annotations.DebuggingUse
import org.opendc.simulator.network.components.invalidatable.IInvalidatable
import org.opendc.simulator.network.components.invalidatable.Invalidatable
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimDevConfig
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.fwpool.FWConfig
import org.opendc.simulator.network.simscope.fwpool.FWDispenser
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.simscope.fwpool.FWPool

class TestEmitter : EvntEmitter<TestEmitter> by IEvntEmitter()

abstract class TestEvnt : Evnt<TestEmitter, TestEvnt>() {
    companion object : FWId<TestEvnt>
}

abstract class InvTestEvnt : Evnt<TestEmitter, InvTestEvnt>(), Invalidatable {
    companion object : FWId<InvTestEvnt>
}

class EvntTest : FunSpec({
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Test Setup (shared by all tests in this test class)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    lateinit var rootScope: NetSimRootScope
    lateinit var evntDisp: FWDispenser<TestEvnt>
    lateinit var evntPool: FWPool<TestEvnt, TestEvnt.Companion>
    lateinit var invEvntDisp: FWDispenser<InvTestEvnt>
    lateinit var invEvntPool: FWPool<InvTestEvnt, InvTestEvnt.Companion>

    // Set up the network simulation context for each test.
    // Used instead of `beforeEach` because the test `coroutineContext` is needed,
    // and it is not available in `beforeEach`.
    suspend fun TestScope.setUp() {
        rootScope =
            NetSimRootScope(
                NetSimConfig(
                    netSimDevConfig =
                        NetSimDevConfig(
                            flyWeightConfig =
                                FWConfig(
                                    // Config set so that these metrics are updated during simulation.
                                    poolMaxSize = Int.MAX_VALUE,
                                    subPoolMaxIdle = Int.MAX_VALUE,
                                    subPoolMaxSize = Int.MAX_VALUE,
                                ),
                        ),
                ),
            )
        rootScope.launch {
            evntDisp =
                poolAggr.getOrAdd(TestEvnt as FWId<TestEvnt>) { pool, idx ->
                    object : TestEvnt() {
                        override val pool = pool
                        override val poolIdx = idx
                    }
                }
            invEvntDisp =
                poolAggr.getOrAdd(InvTestEvnt as FWId<InvTestEvnt>) { pool, idx ->
                    val stab = barrier.stabilizer()
                    object : InvTestEvnt(), IInvalidatable {
                        override val pool = pool
                        override val poolIdx = idx
                        override val stabilizer = stab
                    }
                }
            evntPool = poolAggr.getPool(TestEvnt)
            invEvntPool = poolAggr.getPool(InvTestEvnt)
        }.join()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Network Test Logic (repeated for each test class)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    suspend fun FunSpecContainerScope.netTest(
        name: String,
        block: suspend NetSimScope.() -> Unit,
    ) = test(name) {
        setUp()
        rootScope.launch { block() }.join()
        rootScope.cancel()
    }

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

    suspend fun getEvnt(): Evnt<TestEmitter, TestEvnt> = evntDisp.acquire()

    suspend fun getInvEvnt(): Evnt<TestEmitter, InvTestEvnt> = invEvntDisp.acquire()

    context("1 listener") {
        netTest("check received correctly") {
            val emitter = TestEmitter()
            val listener = emitter.evntListener()

            barrier.isStable() shouldBe true

            getEvnt().emit(from = emitter)
            barrier.isStable() shouldBe true

            val e = listener.receiveCatching().getOrThrow()
            barrier.isStable() shouldBe true

            e.handled()
            barrier.isStable() shouldBe true
        }
        netTest("check disposed correctly") {
            val emitter = TestEmitter()
            val listener = emitter.evntListener()

            // Start of test state.
            barrier.isStable() shouldBe true
            evntPool.getNIdle() shouldBe 0
            evntPool.getNObjs() shouldBe 0

            // An event is initialized in the flyweight pool and immediately acquired and emitted.
            getEvnt().emit(from = emitter)
            barrier.isStable() shouldBe true
            evntPool.getNIdle() shouldBe 0
            evntPool.getNObjs() shouldBe 1

            // The event is received by the listener.
            val e = listener.receiveCatching().getOrThrow()
            barrier.isStable() shouldBe true
            evntPool.getNIdle() shouldBe 0
            evntPool.getNObjs() shouldBe 1

            // The event is marked as handled by the only listener;
            // it should be disposed of and return in the flyweight pool.
            e.handled()
            barrier.isStable() shouldBe true
            evntPool.getNIdle() shouldBe 1
            evntPool.getNObjs() shouldBe 1

            // A new event is requested from the pool;
            // The old instance should be acquired since it is idle.
            getEvnt()
            evntPool.getNIdle() shouldBe 0
            evntPool.getNObjs() shouldBe 1

            // A second event is requested from the pool while no idle instances;
            // A new instance should be created.
            getEvnt()
            evntPool.getNIdle() shouldBe 0
            evntPool.getNObjs() shouldBe 2
        }
        netTest("check invalidation") {
            val emitter = TestEmitter()
            val listener = emitter.evntListener()

            // Start of test state.
            barrier.isStable() shouldBe true
            invEvntPool.getNIdle() shouldBe 0
            invEvntPool.getNObjs() shouldBe 0

            // An event is initialized in the flyweight pool and immediately acquired and emitted.
            getInvEvnt().emit(from = emitter)
            barrier.isStable() shouldBe false // [Invalidatable] event isn't handled yet.
            invEvntPool.getNIdle() shouldBe 0
            invEvntPool.getNObjs() shouldBe 1

            // The event is received by the listener.
            val e = listener.receiveCatching().getOrThrow()
            barrier.isStable() shouldBe false // [Invalidatable] event isn't handled yet.
            invEvntPool.getNIdle() shouldBe 0
            invEvntPool.getNObjs() shouldBe 1

            // The event is marked as handled by the only listener;
            // it should be disposed of and return in the flyweight pool.
            e.handled()
            barrier.isStable() shouldBe true // [Invalidatable] event has been handled by all registered listeners.
            invEvntPool.getNIdle() shouldBe 1
            invEvntPool.getNObjs() shouldBe 1
        }
    }
    context("multiple listeners") {
        checkAll(iterations = 5, Arb.int(0..10)) { nListeners ->
            netTest("$nListeners listeners") {
                val emitter = TestEmitter()
                val listeners = (0..<nListeners).map { emitter.evntListener() }

                barrier.isStable() shouldBe true
                getInvEvnt().emit(from = emitter)
                val job =
                    launch {
                        listeners.forEach { l ->
                            launch {
                                delay(Arb.long(0L..1000L).next())
                                barrier.isStable() shouldBe false
                                val e = l.receiveCatching().getOrThrow()
                                barrier.isStable() shouldBe false
                                delay(Arb.long(0L..1000L).next())
                                barrier.isStable() shouldBe false
                                e.handled()
                            }
                        }
                    }

                barrier.awaitStability()
                job.join()
                barrier.isStable() shouldBe true
            }
        }
    }
})
