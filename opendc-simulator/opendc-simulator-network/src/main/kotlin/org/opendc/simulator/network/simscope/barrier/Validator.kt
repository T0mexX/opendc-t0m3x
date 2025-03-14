package org.opendc.simulator.network.simscope.barrier

import org.opendc.simulator.network.components.Network

/**
 * Single components of the [Network] can validate, invalidate,
 * await the network's stability with this object.
 *
 * A [Validator] is bounded to the [NetSimBarrier] it was created for.
 */
internal interface Validator {

    /**
     * Invalidates the stability of the [Network] from the perspective of this [Validator]'s owner.
     * This indicates that the owning component considers itself to be in an unstable state.
     */
    suspend fun invalidate()

    /**
     * Validates the stability of the [Network] from the perspective of this [Validator]'s owner.
     * This indicates that the owning component considers itself to be in a stable state.
     */
    suspend fun validate()

    /**
     * @param block the block to be executed.
     * Executes [block] while this [Validator]'s owner declares unstable state.
     */
    suspend fun <T> whileNotStable(block: () -> T): T

    /**
     * @see NetSimBarrier.checkIsStableWhile
     */
    fun <T> checkIsStableWhile(block: () -> T): T
}
