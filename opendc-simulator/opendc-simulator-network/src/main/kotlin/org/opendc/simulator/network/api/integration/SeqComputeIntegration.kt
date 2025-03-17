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

import kotlinx.coroutines.channels.Channel
import org.opendc.simulator.network.utils.`observable-old`.LazyChangeHndlr
import org.opendc.simulator.network.utils.`observable-old`.handlers.LazyEventHndlr
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * This class is used as a [CoroutineContext] element so that all coroutines that run network
 * simulation have access to [seqChangeHndlr] through [seqChangeHndlrChl].
 *
 * It allows event handlers to not be invoked instantly in parallel, but be queued in [seqChangeHndlr]
 * and invoked in the future in a sequential manner through [executeSequentialHndlrs].
 */
public abstract class SeqComputeIntegration : AbstractCoroutineContextElement(Key) {
    /**
     * Stores property change handlers with already set parameters, to be invoked when [executeSequentialHndlrs] is called.
     */
    private val seqChangeHndlr = Channel<LazyChangeHndlr<*, *>>(Channel.UNLIMITED)

    /**
     * Stores event handlers with already set parameters, to be invoked when [executeSequentialHndlrs] is called.
     */
    private val seqEventHndlr = Channel<LazyEventHndlr<*>>(Channel.UNLIMITED)


    /**
     * Executes all the pending events handlers that have been marked as sequential.
     */
    public fun executeSequentialHndlrs(): Int {
        var count = 0
        while (true) {
            seqChangeHndlr.tryReceive().getOrNull()?.invoke() ?: break
            count++
        }
        return count
    }

    internal companion object Key : CoroutineContext.Key<SeqComputeIntegration> {
        /**
         * Allows suspend functions in the network scope (the one running the network)
         * to invoke this method and retrieve [seqChangeHndlr].
         */
        fun CoroutineContext.seqChangeHndlrChl(): Channel<LazyChangeHndlr<*, *>> =
            this[SeqComputeIntegration]
                ?.seqChangeHndlr
                ?: throw IllegalStateException("coroutine context $this does not provide a `SeqComputeIntegration`, but one is needed")

        fun CoroutineContext.seqEventHndlrChl(): Channel<LazyEventHndlr<*>> =
            this[SeqComputeIntegration]
                ?.seqEventHndlr
                ?: throw IllegalStateException("coroutine context $this does not provide a `SeqComputeIntegration`, but one is needed")
    }

    /**
     * This mode allows some classes to avoid initializing unused data-structures.
     */
    public enum class Mode {
        /**
         * Use [SEQUENTIAL] if only events handlers that should execute sequentially will be set.
         */
        SEQUENTIAL,

        /**
         * Use [SUSPENDING] if only events handlers that can be executed in parallel will be set.
         */
        SUSPENDING,

        BOTH,
    }
}
