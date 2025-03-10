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

package org.opendc.simulator.network.components.stability

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that allows to check whether a specific block
 * of code is executed in its entirety while the network is stable, else throwing exception.
 * The interface provided by this class does not allow to instantiate invalidators or reset the validator stat
 *
 * Useful to validate code.
 */
internal abstract class NetworkStabilityChecker : AbstractCoroutineContextElement(Key) {
    /**
     * Checks that the network remains stable while [block] is executed.
     * If network is invalidated while block is being executed an
     * [IllegalStateException] is thrown.
     *
     * Useful to check consistency. **This function should never be
     * invoked in any node runner coroutine**, since if it is invoked it means that the node is not
     * stable (not suspended in receiving update) and an exception will be thrown. It is useful for methods
     * that need to access properties that are modified during the node runner main loop from another coroutine.
     *
     * It is the caller responsibility to use the correct stability
     * checker (associated to the correct network in case more than 1 is active).
     *
     * @throws IllegalStateException
     */
    internal abstract suspend fun <T> checkIsStableWhile(block: suspend () -> T): T

    /**
     * @return `true` if the network is currently stable, `false` otherwise.
     */
    internal abstract fun isStable(): Boolean

    companion object Key : CoroutineContext.Key<NetworkStabilityChecker> {
        fun CoroutineContext.getNetStabilityChecker(): NetworkStabilityChecker =
            this[NetworkStabilityChecker]
                ?: throw IllegalStateException("coroutine context $this does not provide a `networkStabilityChecker`, but one is needed")
    }
}
