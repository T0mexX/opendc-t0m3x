/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.components.internalstructs

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.stability.NetworkStabilityValidator.Invalidator
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.utils.logger

/**
 * Channel that collects all incoming [RateUpdt]s that
 * a single node receives (Independently of where from).
 */
internal class UpdateChl private constructor(
    private val chl: Channel<RateUpdt>,
) : Channel<RateUpdt> by chl {
    internal constructor() :
        this(chl = Channel<RateUpdt>(Channel.UNLIMITED))

    /**
     * Used to invalidate the network stability if any updates are underway.
     */
    private var invalidator: Invalidator? = null

    /**
     * Number of pending updates to be collected from the channel.
     */
    var pending: Int = 1
    private val pendingLock = Mutex()

    /**
     * Clears the channel, without processing the elements.
     */
    fun clear() {
        do {
            val res = chl.tryReceive()
        }
        while (res.getOrNull() != null)
    }

    /**
     * Makes the channel use the invalidator [inv] to invalidate the network stability state.
     */
    suspend fun withInvalidator(inv: Invalidator): UpdateChl {
        invalidator = inv
        pendingLock.withLock { if (pending > 0) inv.invalidate() else inv.validate() }
        return this
    }

    /**
     * Suspending implementation of [tryReceive].
     */
    @OptIn(InternalCoroutinesApi::class)
    suspend fun tryReceiveSus(): ChannelResult<RateUpdt> {
        val chlResult = chl.tryReceive()
        return chlResult.getOrNull()?.let {
            pendingLock.withLock {
                pending--
                check(pending > 0)
            }

            chlResult
        } ?: ChannelResult.failure()
    }

    @Deprecated(
        level = DeprecationLevel.ERROR,
        message = "use suspending version instead",
        replaceWith = ReplaceWith("tryReceiveSus()"),
    )
    override fun tryReceive(): ChannelResult<RateUpdt> = throw UnsupportedOperationException()

    override suspend fun receive(): RateUpdt {
        pendingLock.withLock {
            pending--
            if (pending == 0) invalidator?.validate()
        }

        return chl.receive()
    }

    override suspend fun send(element: RateUpdt) {
        if (element.isEmpty()) return
        pendingLock.withLock {
            pending++
            if (pending == 1) invalidator?.invalidate()
        }

        chl.send(element)
    }

    suspend fun <T> whileUpdtProcessingLocked(block: suspend () -> T): T = block() // TODO

    companion object {
        val log by logger()
    }
}
