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

package org.opendc.simulator.network.simscope.barrier

import org.opendc.simulator.network.simscope.NetSimConfig
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Single components of the [Network] can validate, invalidate,
 * await the network's stability with this object.
 *
 * A [NetSimStabilizer] is bounded to the [NetSimBarrier] it was created for.
 */
internal abstract class NetSimStabilizer internal constructor() : AbstractCoroutineContextElement(Key) {
    protected abstract val netSimConfig: NetSimConfig
    internal abstract val isValidated: Boolean

    /**
     * Manages the validation state of the component.
     *
     * - Each call to [invalidate] marks the component as unstable.
     * - Each call to [validate] offsets a previous [invalidate] call.
     * - The component is considered **stable** if the number of [invalidate] and [validate] calls are equal.
     * - Invalidation is **cumulative**,
     * meaning multiple calls to [invalidate] require the same number of [validate] calls to restore stability.
     */
    internal abstract suspend fun invalidate()

    /**
     * Manages the validation state of the component.
     *
     * - Each call to [invalidate] marks the component as unstable.
     * - Each call to [validate] offsets a previous [invalidate] call.
     * - The component is considered **stable** if the number of [invalidate] and [validate] calls are equal.
     * - Invalidation is **cumulative**,
     * meaning multiple calls to [invalidate] require the same number of [validate] calls to restore stability.
     */
    internal abstract suspend fun validate()

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Invalidated Block Execution
    // /// Logic concerning executing a block while the network invalidated.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Executes block while `this` [NetSimStabilizer] is invalidated,
     * which implies the [Network] is also invalidated.
     *
     * @param block To be executed while `this` [NetSimStabilizer] is invalidated.
     */
    internal suspend fun <T> whileInvalidated(block: suspend () -> T): T {
        invalidate()

        return try {
            block()
        } finally {
            validate()
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stable Block Execution
    // /// Logic concerning executing a block while the network/component is stable.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @see NetSimBarrier.whileStable
     */
    internal abstract suspend fun <T> whileNetStable(
        netSimStabilityMode: NetSimStabilityMode = netSimConfig.stabilityMode,
        block: suspend () -> T,
    ): T

    internal abstract suspend fun awaitStability()

    internal companion object Key : CoroutineContext.Key<NetSimStabilizer>
}
