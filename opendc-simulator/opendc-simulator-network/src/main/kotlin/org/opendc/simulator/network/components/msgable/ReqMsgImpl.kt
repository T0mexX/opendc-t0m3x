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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.simscope.fwpool.FWPool
import org.opendc.simulator.network.utils.Idx

/**
 * TODO
 */
internal abstract class ReqMsgImpl<T : Msgable<T>, A, Self : ReqMsg<T, A, Self>>(
    pool: FWPool<Self, FWId<Self>>,
    poolIdx: Idx,
) : ReqMsg<T, A, Self>, MsgImpl<T, Self>(pool, poolIdx) {
    /**
     * TODO
     */
    private val resp = MutableStateFlow<A?>(null)

    /**
     * TODO
     */
    override suspend fun awaitResponse(): A =
        resp.first {
            it != null
        }!!.also { dispose() }

    /**
     * TODO
     */
    final override suspend fun reset(builderBlock: (suspend Self.() -> Unit)?): Self {
        @Suppress("UNCHECKED_CAST")
        this as Self

        state.emit(Msg.State.UNTRACKED)
        sender = null
        resp.emit(null)

        builderBlock?.invoke(this)

        return this
    }

    /**
     * TODO
     */
    suspend fun respond(response: A) {
        resp.emit(response)
    }
}
