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
import org.opendc.common.units.Timestamp
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
        rootScope = NetSimRootScope(coroutineContext[Job]!!) // Add the child job to propagate exceptions.
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
        delay(1000L)
        println(rootScope.fmtCoTree())
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
            tracker.newFrag(Frag1)
            f.msgAsyncFragInit(target = DataSize.ofGb(4), fragId = Frag1)
            f.msgAsyncSetDemand(DataRate.ofGbps(1), fragId = Frag1)
            barrier.awaitStability()

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
            tracker.newFrag(Frag1)
            f1.msgAsyncFragInit(target = DataSize.ofGb(3), fragId = Frag1)
            f2.msgAsyncFragInit(target = DataSize.ofGb(4), fragId = Frag1)
            f1.msgAsyncSetDemand(dmnd = DataRate.ofGbps(1), fragId = Frag1)
            f2.msgAsyncSetDemand(dmnd = DataRate.ofGbps(1), fragId = Frag1)
            barrier.awaitStability()

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
        netTest("time remaining decreases (1 flow)") {
            var newTs = Timestamp.zero
            val f = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f)
            tracker.on1ComplTsDecreased = { _, new -> newTs = new }

            // Set up flows.
            tracker.newFrag(Frag1)
            f.msgAsyncFragInit(target = DataSize.ofGb(4), fragId = Frag1)
            f.msgAsyncSetDemand(DataRate.ofGbps(1), fragId = Frag1)
            barrier.awaitStability()

            // Initial time remaining.
            tracker.tsFor1Compl() shouldBeEqual Timestamp.ofEpochSec(4)

            // Time remaining decrease.
            f.msgAsyncSetDemand(DataRate.ofGbps(2), fragId = Frag1)
            barrier.awaitStability()
            newTs shouldBeEqual Timestamp.ofEpochSec(2)
            tracker.tsFor1Compl() shouldBeEqual Timestamp.ofEpochSec(2)
        }
        netTest("time remaining increase (1 flow)") {
            var newTs = Timestamp.zero
            val f = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f)
            tracker.onAllComplTsIncreased = { _, new -> newTs = new }

            // Set up flows.
            tracker.newFrag(Frag1)
            f.msgAsyncFragInit(target = DataSize.ofGb(4), fragId = Frag1)
            f.msgAsyncSetDemand(DataRate.ofGbps(1), fragId = Frag1)
            barrier.awaitStability()

            // Initial time remaining.
            tracker.tsFor1Compl() shouldBeEqual Timestamp.ofEpochSec(4)

            // Time remaining increase.
            f.msgAsyncSetDemand(DataRate.ofGbps(0.5), Frag1)
            barrier.awaitStability()

            newTs shouldBeEqual Timestamp.ofEpochSec(8)
            tracker.tsFor1Compl() shouldBeEqual Timestamp.ofEpochSec(8)
        }
        netTest("time remaining decreases (2 flows)") {
            var firstTs = Timestamp.zero
            var allTs = Timestamp.zero
            val f1 = iFace.startFlowFromInet()
            val f2 = iFace.startFlowFromInet()

            // Set up tracker.
            val tracker = NetFTracker(f1, f2)
            tracker.on1ComplTsDecreased = { _, new -> firstTs = new }
            tracker.onAllComplTsDecreased = { _, new -> allTs = new }

            // Set up flows.
            tracker.newFrag(Frag1)
            f1.msgAsyncFragInit(target = DataSize.ofGb(4), fragId = Frag1)
            f1.msgAsyncSetDemand(DataRate.ofGbps(1), fragId = Frag1)
            f2.msgAsyncFragInit(target = DataSize.ofGb(4), fragId = Frag1)
            f2.msgAsyncSetDemand(DataRate.ofGbps(1), fragId = Frag1)

            barrier.awaitStability()

            // Initial time remaining.
            tracker.tsFor1Compl() shouldBe Timestamp.ofEpochSec(4)
            firstTs = tracker.tsFor1Compl()
            tracker.tsForAllCompl() shouldBeEqual Timestamp.ofEpochSec(4)
            allTs = tracker.tsForAllCompl()

            // Test 1
            f1.msgAsyncSetDemand(DataRate.ofGbps(2), Frag1)
            barrier.awaitStability()
            firstTs shouldBeEqual Timestamp.ofEpochSec(2)
            allTs shouldBeEqual Timestamp.ofEpochSec(4)

            tracker.tsFor1Compl() shouldBeEqual Timestamp.ofEpochSec(2)
            tracker.tsForAllCompl() shouldBeEqual Timestamp.ofEpochSec(4)

//            // Test 2
//            f1.msgAsyncSetDemand(DataRate.ofGbps(1))
        }
    }
}) {
    object Frag1
    object Frag2
    object Frag3
    object Frag4
}
