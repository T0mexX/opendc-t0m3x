package org.opendc.simulator.network.simscope.barrier

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.simscope.NetSimConfig
import kotlin.coroutines.coroutineContext
import org.opendc.simulator.network.simscope.NetSimScope

internal class NetSimBarrier private constructor(
    private val netSimConfig: NetSimConfig
): AbstractCoroutineContextElement(Key) {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Properties: counting invalidations at this barrier
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var invalidCount: Int = 0
    private val countMtx = Mutex()

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Properties: stability validation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val stabilityMtx = Mutex()
    private var shouldBeStableCtx: CoroutineContext? = null
    private val shouldBeStableMtx = Mutex()

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Properties: logic for constructing a new `Validator`
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private lateinit var childBarrier: NetSimBarrier

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @return a new [Validator].
     */
    internal suspend fun validator(): Validator = newInvalidatorMtx.withLock {
        if (invalidatorCount < 10) {
            invalidatorCount++
            ValidatorImpl()
        } else if (this::childBarrier.isInitialized) {
            childBarrier.validator()
        } else {
            childBarrier = NetSimBarrier(parentValidator = ValidatorImpl())
            childBarrier.validator()
        }
    }

    /**
     * Suspends until the network is stable (All [Validator]s validated their status).
     */
    internal suspend fun awaitStability() {
        rootBarrier
            ?.awaitStability()
            ?: let {
                stabilityMtx.lock()
                stabilityMtx.unlock()
            }
    }

    /**
     * @param block the block to be executed while the [Network] is stable.
     *
     * If the [NetSimScope] [NetSimConfig.stabilityChecks] is disabled,
     * this method simply executes the block.
     *
     * [IllegalStateException] is thrown by the coroutine that tries to invalidate
     * the [Network] while [block] is being executed.
     */
    internal suspend fun <T> checkIsStableWhile(block: () -> T): T {
        return if (netSimConfig.stabilityChecks) {
            try {
                // Use the `coroutineContext` as a coroutine id.
                shouldBeStableMtx.withLock { shouldBeStableCtx = coroutineContext }

                block()

            // Make sure to remove reset `shouldBeStableCtx` if needed.
            } finally {
                // If no other coroutine is executing a block while checking stability, reset `shouldBeStableCtx`.
                shouldBeStableMtx.withLock {
                    if (shouldBeStableCtx == coroutineContext) shouldBeStableCtx = null
                }
            }

        // If `NetSimConfig.stabilityChecks` is disabled, simply execute the block.
        } else {
            block()
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ChildBarrier
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private inner class ChildBarrier(private val parentValidator: Validator) {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Properties: counting invalidations at this barrier
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private var invalidCount: Int = 0
        private val countMtx = Mutex()

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Properties: logic for constructing a new `Validator`
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private lateinit var childBarrier: ChildBarrier
        private var invalidatorCount: Int = 0
        private val newInvalidatorMtx = Mutex()

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Methods
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * @return a new [Validator].
         */
        suspend fun validator(): Validator = newInvalidatorMtx.withLock {
            if (invalidatorCount < 10) {
                invalidatorCount++
                ValidatorImpl()
            } else if (this::childBarrier.isInitialized) {
                childBarrier.validator()
            } else {
                childBarrier = ChildBarrier(parentValidator = ValidatorImpl())
                childBarrier.validator()
            }
        }

        /**
         * @see Validator
         */
        private inner class ValidatorImpl: Validator {
            override suspend fun invalidate() {
                TODO("Not yet implemented")
            }

            override suspend fun validate() {
                TODO("Not yet implemented")
            }

            override suspend fun <T> whileNotStable(block: () -> T): T {
                TODO("Not yet implemented")
            }

            override fun <T> checkIsStableWhile(block: () -> T): T {
                TODO("Not yet implemented")
            }
        }
    }

    internal companion object Key : CoroutineContext.Key<NetSimBarrier>
}
