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

package org.opendc.simulator.network.components.invalidatable.internals

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TODO
 * @property receiver If defined, it is considered the only component that receives from this channel as an [Invalidatable] component,
 * and invalidation logic is run to ensure network stability consistency.
 */
internal open class InvalidatorChl<T> private constructor(
    private val delegatedChl: Channel<T>,
    private val receiver: Invalidatable? = null,
) : Channel<T> by delegatedChl {
    constructor(receiver: Invalidatable? = null) :
        this(delegatedChl = Channel<T>(Channel.UNLIMITED), receiver = receiver)

    /**
     * Number of pending updates to be collected from the channel.
     */
    var pending: Int = 1
    private val pendingMtx = Mutex()

    /**
     * Suspending implementation of [tryReceive]
     * in order to invalidate [receiver].
     */
    suspend fun tryReceiveValidate(): ChannelResult<T> {
        val res = delegatedChl.tryReceive()
        if (res.isSuccess) {
            pendingMtx.withLock {
                pending--
            }
            (res.getOrThrow() as? Invalidatable)?.validate()
        }
        return res
    }

    @Deprecated(
        message = "This method must not be called",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("tryReceiveValidate()"),
    )
    override fun tryReceive(): ChannelResult<T> = throw UnsupportedOperationException()

    /**
     * This override also works when [onReceive] is used, since [onReceive]
     * is called only once when the method will succeed.
     */
    override suspend fun receive(): T {
        receiver?.let { receiver ->
            pendingMtx.withLock {
                if (--pending == 0) receiver.validate()
            }
        }

        return delegatedChl.receive().also {
            if (it is Invalidatable) it.validate()
        }
    }

    override suspend fun send(element: T) {
        receiver?.let { receiver ->
            pendingMtx.withLock {
                if (++pending == 1) receiver.invalidate()
            }
        }

        delegatedChl.send(element)
    }
}
