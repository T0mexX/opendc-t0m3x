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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.simulator.network.api.NetIFace
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.SetOnce
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

internal inline fun <reified T> netBlking(
    netScope: NetSimScope,
    ignoreCancellationExc: Boolean = false,
    crossinline block: suspend NetSimScope.() -> T,
): T = try {
    runBlocking(netScope.coroutineContext) {
        with(netScope) {
            block()
        }
    }
} catch (e: CancellationException) {
    if (ignoreCancellationExc && T::class == Unit::class) Unit as T
    else throw e
}

/**
 * @param netScope The coroutine network env [block] is going to be executed in.
 * @param ignoreCancellationExc If [netScope] is/was canceled then:
 * - If [ignoreCancellationExc] is `true` and [T] is [Unit] invocation will silently do nothing.
 * - Otherwise [CancellationException] will be thrown.
 * @param block The block of code to be executed by [netScope] while blocking the current thread.
 */
@Deprecated(message = "less performant than netBlking")
@OptIn(ExperimentalCoroutinesApi::class)
internal inline fun <reified T> latched(
    netScope: NetSimScope,
    ignoreCancellationExc: Boolean = false,
    crossinline block: suspend NetSimScope.() -> T,
): T {
    val latch = CountDownLatch(1)
    val res = netScope.async coScope@{
        block(netScope)
    }

    // This callback is invoked even if the env is already completed/cancelled.
    res.invokeOnCompletion {
        latch.countDown()
    }
    latch.await()

    return try {
        res.getCompleted()
    } catch (e: CancellationException) {
        if (ignoreCancellationExc && T::class == Unit::class) return Unit as T
        else throw e
    }
}

/**
 * @param netScope The coroutine network env [block] is going to be executed in.
 * @param ignoreCancellationExc If [netScope] is/was canceled then:
 * - If [ignoreCancellationExc] is `true` and [T] is [Unit] invocation will silently do nothing.
 * - Otherwise [CancellationException] will be thrown.
 * @param block The block of code to be executed by [netScope] while blocking the current thread.
 */
@Deprecated(message = "less performant than netBlking")
internal inline fun <reified T> withFuture(
    netScope: NetSimScope,
    ignoreCancellationExc: Boolean = false,
    crossinline block: suspend NetSimScope.() -> T,
): T {
    val fut = CompletableFuture<T>()

    netScope.launch {
        fut.complete(block())
    }.invokeOnCompletion { throwable ->
        throwable?.let { fut.completeExceptionally(it) }
    }

    return try {
        fut.get()
    } catch (e: CancellationException) {
        if (ignoreCancellationExc && T::class == Unit::class) return Unit as T
        else throw e
    }
}

// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// `SimTraceWorkload` `startNetworkFrag` Optimization Logic
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

public class JNetStartFragOpt(private val jNetIface: JNetIFace) {
    public var jfTx: JNetFlow by SetOnce()
    public var jfRx: JNetFlow by SetOnce()
    public var jTracker: JNetFTracker? by SetOnce()

    /**
     * TODO
     */
    public fun startNetFrag(
        fragId: Any,
        txDmndKbps: Double,
        txTargetKb: Double,
        rxDmndKbps: Double,
        rxTargetKb: Double,
    ): Unit = netBlking(jNetIface.scope) {
        //
        // If tracker not yet set then set it to `null`.
        val tracker = try {
            jTracker?.tracker
        } catch (e: IllegalStateException) {
            jTracker = null
            null
        }

        //
        // Convert raw doubles to units.
        val f1Dmnd = DataRate.ofKbps(txDmndKbps)
        val f1Target = DataSize.ofKb(txTargetKb)
        val f2Dmnd = DataRate.ofKbps(rxDmndKbps)
        val f2Target = DataSize.ofKb(rxTargetKb)

        //
        // Execute all suspending calls using only 1 `netBlocking` call.
        jfTx.f.msgAsyncFragInit(target = f1Target, fragId = fragId)
        jfRx.f.msgAsyncFragInit(target = f2Target, fragId = fragId)
        jfTx.f.msgAsyncSetDemand(dmnd = f1Dmnd, fragId = fragId)
        jfRx.f.msgAsyncSetDemand(dmnd = f2Dmnd, fragId = fragId)
        tracker?.newFrag(fragId = fragId)
    }
}

private fun main() {
    val scope1 = NetSimRootScope(Dispatchers.Default)

    latched(scope1) {
        println("CIAO1")
    }

    scope1.cancel()

    latched(scope1, true) {
        println("CIAO1")
    }

    val scope2 = NetSimRootScope(Dispatchers.Default)

    withFuture(scope2) {
        println("CIAO2")
    }

    scope2.cancel()

    withFuture(scope2, ignoreCancellationExc = true) {
        println("CIAO2")
    }

    val scope3 = NetSimRootScope(Dispatchers.Default)

    netBlking(scope3) {
        println("CIAO3")
    }

    scope3.cancel()

    netBlking(scope3) {
        println("CIAO3")
    }
}
