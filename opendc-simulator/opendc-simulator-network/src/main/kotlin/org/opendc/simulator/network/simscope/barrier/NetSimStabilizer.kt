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
internal abstract class NetSimStabilizer internal constructor(

): AbstractCoroutineContextElement(Key) {
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Invalidated Block Execution
    ///// Logic concerning executing a block while the network invalidated.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stable Block Execution
    ///// Logic concerning executing a block while the network/component is stable.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
