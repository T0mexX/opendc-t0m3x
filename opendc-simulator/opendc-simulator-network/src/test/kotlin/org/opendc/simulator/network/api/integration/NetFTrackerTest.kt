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

package org.opendc.simulator.network.api.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.FunSpecContainerScope
import io.kotest.core.test.TestScope
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.opendc.common.annotations.DebuggingUse
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.TimeDelta
import org.opendc.simulator.network.api.NetIFace
import org.opendc.simulator.network.components.networks.custom.CustomNetwork
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimTmSrc

@OptIn(DebuggingUse::class)
class NetFTrackerTest : FunSpec({
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Test Setup (shared by all tests in this test class)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    lateinit var rootScope: NetSimRootScope
    lateinit var iFace: NetIFace
    lateinit var tmSrc: NetSimTmSrc.Internal

    // Used instead of `beforeEach` because the test `coroutineContext` is needed,
    // and it is not available in `beforeEach`.
    suspend fun TestScope.setUp() {
        rootScope = NetSimRootScope(coroutineContext + Job(coroutineContext[Job]!!)) // Add the child job to propagate exceptions.
        rootScope.launch {
            //
            // Create [CustomNetwork] with 1 terminal [t], 1 global [switch] s and internet abstract node [inet].
            // Such that t <-> s <-> inet.
            CustomNetwork()
            val net = this@launch.net as CustomNetwork
            val t = Terminal(nPorts = 1, portSpeed = DataRate.ofGbps(1000))
            val s = Switch(nPorts = 2, portSpeed = DataRate.ofGbps(1000), global = true)
            net + t
            net + s
            t.msgSyncConnect(s)

            iFace = NetIFace(t)
            tmSrc = rootScope.tmSrc as NetSimTmSrc.Internal
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

    context("simple triggers") {
        netTest("1 fragment completed") {
            val triggered = MutableStateFlow(0)
            val f = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f)
            tracker.on1FFragCompl = { _, _ -> triggered.emit(triggered.value + 1) }

            // Set up flow.
            f.msgAsyncSetDemand(DataRate.ofGbps(1))
            f.msgAsyncFragInit(target = DataSize.ofGb(4))
            barrier.awaitStability()
            tracker.reset()

            // half of the fragment completed.
            tmSrc.advanceBy(TimeDelta.ofSec(2))
            sync()
            triggered.value shouldBe 0

            // The whole fragment completed.
            tmSrc.advanceBy(TimeDelta.ofSec(2))
            sync()
            triggered.value shouldBe 1

            // No more triggers.
            tmSrc.advanceBy(TimeDelta.ofSec(2))
            sync()
            triggered.value shouldBe 1
            println(fmtCoTree())
        }
        netTest("all fragments completed") {
            var triggeredAll = false
            var triggered1 = 0
            val f1 = iFace.startFlowFromInet()
            val f2 = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f1, f2)
            tracker.onAllFFragCompl = { triggeredAll = true }
            tracker.on1FFragCompl = { _, _ -> triggered1++ }

            // Set up flows.
            f1.msgAsyncFragInit(target = DataSize.ofGb(3))
            f2.msgAsyncFragInit(target = DataSize.ofGb(4))
            f1.msgAsyncSetDemand(demand = DataRate.ofGbps(1))
            f2.msgAsyncSetDemand(demand = DataRate.ofGbps(1))
            barrier.awaitStability()
            tracker.reset()

            // No fragment completed
            tmSrc.advanceBy(TimeDelta.ofSec(2))
            sync()
            triggeredAll shouldBe false
            triggered1 shouldBe 0

            // 1 fragment completed.
            tmSrc.advanceBy(TimeDelta.ofSec(1))
            sync()
            triggeredAll shouldBe false
            triggered1 shouldBe 1

            // 2 fragments completed.
            tmSrc.advanceBy(TimeDelta.ofSec(1))
            sync()
            triggeredAll shouldBe true
            triggered1 shouldBe 2
        }
        netTest("time remaining decreases") {
            var newTmRm = TimeDelta.zero
            val f = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f)
            tracker.onTmRmDecrease = { _, new -> newTmRm = new }

            // Set up flows.
            f.msgAsyncSetDemand(DataRate.ofGbps(1))
            f.msgAsyncFragInit(target = DataSize.ofGb(4))
            tracker.reset()
            barrier.awaitStability()

            // Initial time remaining.
            tracker.tmRm() shouldBeEqual TimeDelta.ofSec(4)

            // Time remaining decrease.
            f.msgAsyncSetDemand(DataRate.ofGbps(2))
            delay(1000)
            barrier.awaitStability()
            newTmRm shouldBeEqual TimeDelta.ofSec(2)
            tracker.tmRm() shouldBeEqual TimeDelta.ofSec(2)
        }
        netTest("time remaining increase") {
            var newTmRm = TimeDelta.zero
            val f = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f)
            tracker.onTmRmIncrease = { _, new -> newTmRm = new }

            // Set up flows.
            f.msgAsyncSetDemand(DataRate.ofGbps(1))
            f.msgAsyncFragInit(target = DataSize.ofGb(4))
            tracker.reset()
            barrier.awaitStability()

            // Initial time remaining.
            tracker.tmRm() shouldBeEqual TimeDelta.ofSec(4)

            // Time remaining increase.
            f.msgAsyncSetDemand(DataRate.ofGbps(0.5))
            barrier.awaitStability()
            newTmRm shouldBeEqual TimeDelta.ofSec(8)
            tracker.tmRm() shouldBeEqual TimeDelta.ofSec(8)
        }
    }
})
