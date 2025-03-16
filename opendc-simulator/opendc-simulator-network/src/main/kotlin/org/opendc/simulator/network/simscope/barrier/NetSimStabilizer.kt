package org.opendc.simulator.network.simscope.barrier

import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Single components of the [Network] can validate, invalidate,
 * await the network's stability with this object.
 *
 * A [NetSimStabilizer] is bounded to the [NetSimBarrier] it was created for.
 */
internal abstract class NetSimStabilizer : AbstractCoroutineContextElement(Key) {
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
    abstract suspend fun invalidate()

    /**
     * Manages the validation state of the component.
     *
     * - Each call to [invalidate] marks the component as unstable.
     * - Each call to [validate] offsets a previous [invalidate] call.
     * - The component is considered **stable** if the number of [invalidate] and [validate] calls are equal.
     * - Invalidation is **cumulative**,
     * meaning multiple calls to [invalidate] require the same number of [validate] calls to restore stability.
     */
    abstract suspend fun validate()


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Invalidated Block Execution
    ///// Logic concerning executing a block while the network invalidated.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//    /**
//     * Executes [block] while the [Network] is invalidated.
//     *
//     * If a [NetSimStabilizer] exists in the current
//     * [CoroutineContext], its invalidation alone is enough.
//     * This optimization improves performance when the
//     * context's stabilizer is already invalidated.
//     *
//     * @param block To be executed while the [Network] is invalidated.
//     */
//    context(CoroutineContext)
//    suspend fun <T> whileNetInvalidated(block: () -> T): T {
//        // If a `NetSimStabilizer` in current context.
//        return this@CoroutineContext[NetSimStabilizer]?.let {
//            // If the `NetSimStabilizer` is the current one, then
//            // execute while `this` is invalidated.
//            if (it === this@NetSimStabilizer) whileInvalidated(block)
//            // Else execute while the one provided in context is invalidated.
//            // This may improve performance if the provided one is already invalidated.
//            else it.whileNetInvalidated(block)
//        // If no `NetSimStabilizer` is provided in context, then
//        // execute while `this` is invalidated.
//        } ?: whileInvalidated(block)
//    }

    /**
     * Executes block while `this` [NetSimStabilizer] is invalidated,
     * which implies the [Network] is also invalidated.
     *
     * @param block To be executed while `this` [NetSimStabilizer] is invalidated.
     */
    suspend fun <T> whileInvalidated(block: suspend () -> T): T {
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
    abstract suspend fun <T> whileNetStable(
        netSimStabilityMode: NetSimStabilityMode = netSimConfig.stabilityMode,
        block: suspend () -> T,
    ): T

    abstract suspend fun awaitStability()

    companion object Key : CoroutineContext.Key<NetSimStabilizer>
}
