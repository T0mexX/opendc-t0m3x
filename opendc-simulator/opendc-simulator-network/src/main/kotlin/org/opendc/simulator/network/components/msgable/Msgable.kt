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

package org.opendc.simulator.network.components.msgable

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.annotations.DebuggingUse
import org.opendc.common.annotations.ProtectedUse
import org.opendc.simulator.network.components.invalidatable.InvalidatorChl
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier.Key.getInvalidated

/**
 * TODO
 */
internal interface Msgable<T : Msgable<T>> {
    /**
     * TODO
     */
    val msgChl: SendChannel<Msg<T, *>>

    context(NetSimScope) @OptIn(DelicateCoroutinesApi::class, DebuggingUse::class) @ProtectedUse
    suspend fun drainMsgChl() {
        @Suppress("UNCHECKED_CAST")
        val msgChl = msgChl as InvalidatorChl<Msg<T, *>>
        // Close the msg channel so that no more [Msg]s can be received.
        msgChl.close()
        var nDrained = 0

        // Handled the [Msg]s that are still in [msgChl].
        while (msgChl.isClosedForReceive.not()) {
            msgChl.tryReceiveValidate().getOrThrow().markUndelivered()
            nDrained++
        }
        log.debug("{} was cancelled with {} drained msgs", this, nDrained)
    }
}
