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

@file:OptIn(InternalODCNetworkApi::class)

package org.opendc.simulator.network.components.evntemitter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.simscope.fwpool.IFW
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.InternalODCNetworkApi
import kotlin.coroutines.coroutineContext

/*
Abstract class with all abstract members is used instead
of an interface to be able to specify internal members.
 */

/**
 * TODO
 */
public abstract class Evnt<T : EvntEmitter<T>, Self : Evnt<T, Self>> : IFW<Self> {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Private Implementation
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val nHandledMtx = Mutex()
    private var nHandled: Int = 0
    private val state = MutableStateFlow(State.UNTRACKED)
    private var emitter: CoroutineID? = null

    private enum class State {
        HANDLED,
        PENDING,
        UNTRACKED,
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * TODO
     */
    public suspend fun handled() {
        nHandledMtx.withLock {
            if (++nHandled == nCollectors) {
                // If the fact that this event is not yet handled by all collectors invalidates the network
                // (hence the evnt is invalidatable), then validate it once it is handled.
                (this as? Invalidatable)?.validate()

                // If the emitter tracks evnt state, then communicate that msg
                // was handled (evnt is going to be disposed by the emitter)
                if (state.value == State.PENDING) {
                    state.emit(State.HANDLED)

                    // Else, after evnt is handled, it can safely be disposed of.
                } else {
                    dispose()
                }
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Internal
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    internal var nCollectors: Int = 0

    /**
     * TODO
     */
    internal suspend fun awaitHandling(): Self {
        state.first {
            // If `state` is `null`, msg was sent with `dispose = true` which means
            // the message flyweight object might have been reused by now.
            assert(emitter == coroutineContext[CoroutineID]) { "await on recycled msg" }
            it == State.HANDLED
        }
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    internal suspend fun reset(builderBlock: (suspend Self.() -> Unit)?): Self {
        @Suppress("UNCHECKED_CAST")
        this as Self

        state.emit(State.UNTRACKED)
        emitter = null

        builderBlock?.invoke(this)

        return this
    }

    /**
     * TODO
     */
    internal suspend fun emit(
        from: T,
        dispose: Boolean = true,
    ): Self {
        // If dispose is false, `emitter` wants to wait for the evnt to be handled;
        // hence `state` is going to be tracked, and this `msg` is not going to be disposed by the receiver.
        if (dispose.not()) {
            emitter = coroutineContext[CoroutineID]!!
            state.emit(State.PENDING)
        }

        from.emit(this)

        @Suppress("UNCHECKED_CAST")
        return this as Self
    }
}
