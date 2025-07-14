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

import org.opendc.simulator.network.simscope.fwpool.IFW

/**
 * TODO
 */
internal interface Msg<in T, Self : Msg<T, Self>> : IFW<Self>
    where T : Msgable<in T> {
    /**
     * TODO
     */
    suspend fun awaitHandling(): Self

    /**
     * TODO
     */
    context(T)
    suspend fun handle()

    /**
     * TODO
     * Should be called outside of subclasses only on channel closed exceptions
     */
    suspend fun markHandled()

    suspend fun markUndelivered()

    /**
     * TODO
     */
    suspend fun sendTo(
        to: T,
        dispose: Boolean = true,
    ): Self

    /**
     * TODO
     * @param builderBlock
     */
    suspend fun reset(builderBlock: (suspend Self.() -> Unit)? = null): Self

    enum class State {
        HANDLED,
        PENDING,
        UNTRACKED,
        UNDELIVERED,
    }
}
