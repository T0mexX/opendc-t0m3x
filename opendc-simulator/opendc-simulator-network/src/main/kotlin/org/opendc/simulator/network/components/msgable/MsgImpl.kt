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

import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.opendc.common.logger.logger
import org.opendc.simulator.network.components.invalidatable.Invalidatable
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier.Key.getInvalidated
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.simscope.fwpool.FWPool
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.NetCoId
import kotlin.coroutines.coroutineContext

/**
 * TODO
 */
internal abstract class MsgImpl<T, Self : Msg<T, Self>>(
    override val pool: FWPool<Self, FWId<Self>>,
    override val poolIdx: Idx,
) : Msg<T, Self>
    where T : Msgable<T> {
    /**
     * TODO
     */
    protected val state = MutableStateFlow(Msg.State.UNTRACKED)

    /**
     * TODO
     */
    protected var senderCoId: NetCoId? = null

    /**
     * TODO
     */
    final override suspend fun awaitHandling(): Self {
        state.first {
            // If `state` is `null`, msg was sent with `dispose = true` which means
            // the message flyweight object might have been reused by now.
            assert(senderCoId === coroutineContext[NetCoId]!!) { "await on recycled msg" }
            it == Msg.State.HANDLED || it == Msg.State.UNDELIVERED
        }
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    override suspend fun sendTo(
        to: T,
        dispose: Boolean,
    ): Self {
        // If dispose is false, `sender` wants to wait for the msg to be handled;
        // hence `state` is going to be tracked, and this `msg` is not going to be disposed by the receiver.
        if (dispose.not()) {
            senderCoId = coroutineContext[NetCoId]!!
            state.emit(Msg.State.PENDING)
        }

        try {
            to.msgChl.send(this)
        } catch (e: ClosedSendChannelException) {
            log.debug("{} undelivered", this)
            // If unable to send message because the receiver channel has been closed.
            // If the message was tracked, then emit [Undelivered], else just dispose.
            (this as? Invalidatable)?.validate()
            if (state.value == Msg.State.PENDING) state.emit(Msg.State.UNDELIVERED)
            else dispose()
        }

        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    override suspend fun reset(builderBlock: (suspend Self.() -> Unit)?): Self {
        @Suppress("UNCHECKED_CAST")
        this as Self

        state.emit(Msg.State.UNTRACKED)
        senderCoId = null

        builderBlock?.invoke(this)

        return this
    }

    /**
     * TODO
     */
    final override suspend fun markHandled() {
        assert(this !is ReqMsg<*, *, *>)
        assert(state.value != Msg.State.HANDLED)

        // If the sender tracks msg state, then communicate that msg
        // was handled (msg is going to be disposed by the sender)
        if (state.value == Msg.State.PENDING) {
            state.emit(Msg.State.HANDLED)

        // Else, after msg is handled, it can safely be disposed of.
        } else {
            dispose()
        }
    }

    final override suspend fun markUndelivered() {
        assert(state.value != Msg.State.HANDLED)

        if (state.value == Msg.State.PENDING) {
            state.emit(Msg.State.UNDELIVERED)

        } else {
            dispose()
        }
    }

    private companion object {
        val log by logger()
    }
}
