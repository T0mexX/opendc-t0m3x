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

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.isActive
import org.opendc.simulator.network.simscope.NetSimScope
import java.util.concurrent.CountDownLatch

/**
 * Used to make calls to suspending methods in [NetController] from non suspending
 * context while waiting for resolution in the most efficient way possible.
 */
@OptIn(InternalCoroutinesApi::class)
internal inline fun <T> latched(
    netScope: NetSimScope,
    crossinline block: suspend NetSimScope.() -> T,
): T {
    assert(netScope.isActive)
    val latch = CountDownLatch(1)
    var res: T? = null
    var err: Throwable? = null
    val t = Thread.currentThread()
//    println("A")
    netScope.launch coScope@{
//        println("B")
        assert(Thread.currentThread() !== t)
        res = block(netScope)
//        println("C")
    }.invokeOnCompletion { throwable ->
        err = throwable
        latch.countDown()
    }

    latch.await()
    err?.let { throw it }

    return res!!
}
