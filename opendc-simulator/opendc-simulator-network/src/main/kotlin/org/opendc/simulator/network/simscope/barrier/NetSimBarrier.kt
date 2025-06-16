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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal class NetSimBarrier internal constructor(
    private val netSimConfig: NetSimConfig,
) : AbstractCoroutineContextElement(Key) {
    /**
     * Determines if the network is currently in a stable state.
     * - locked => network unstable
     * - unlocked => network stable
     */
    private val stabilityMtx = Mutex()

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stability Enforcing:
    // /// Logic concerning awaiting and then enforcing network stability
    // /// while a block is being executed.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Number of coroutines that are currently enforcing stability.
     */
    private var stabilityEnforcerCount: Int = 0
    private val stabilityEnforcerMtx = Mutex()

    /**
     * - true => `Invalidator`s need to wait to be able to invalidate their state.
     * - false => `Invalidator`s can freely invalidate their state.
     *
     * A [MutableStateFlow] is used instead of a lock so that multiple
     * waiting coroutines can be awakened at the same time
     */
    private val deter = MutableStateFlow(false)

    private suspend fun <T> whileStabilityEnforced(block: suspend () -> T): T {
        stabilityEnforcerMtx.withLock {
            // If this is the first stability enforcer.
            if (++stabilityEnforcerCount == 1) {
                // Prevents invalidations.
                deter.emit(true)
                // Awaits that all `Validators` validated their state.
                awaitStability()
            }
        }

        return try {
            block()
        } finally {
            stabilityEnforcerMtx.withLock {
                // If this is the last stability enforcer, then allows invalidations.
                if (--stabilityEnforcerCount == 0) deter.emit(false)
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stability Checking:
    // /// Logic concerning checking that the network remains stable while
    // /// a block is being executed.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Number of blocks that are currently being executed in the [whileStabilityChecked] method.
     */
    private var shouldBeStableCounter: Int = 0
    private val shouldBeStableMtx = Mutex()

    @Volatile
    private var shouldBeStable: Boolean = false

    /**
     * @param block the block to be executed while the [Network] is stable.
     *
     * [NetSimStabilityException] is thrown by any coroutine that tries to invalidate
     * the [Network] while [block] is being executed.
     */
    private suspend fun <T> whileStabilityChecked(block: suspend () -> T): T {
        return try {
            // If the network is not currently stable, then throw.
            if (stabilityMtx.tryLock().not()) throw NetSimStabilityException()

            shouldBeStableMtx.withLock {
                // If this is the first stability checker, then set `shouldBeStable` to `true`.
                if (++shouldBeStableCounter == 1) shouldBeStable = true
            }

            // Unlock `stabilityMtx` so that other checkers can successfully invoke this method.
            stabilityMtx.unlock()

            block()

            // Make sure to decrease `shouldBeStableCounter`.
        } finally {
            shouldBeStableMtx.withLock {
                // If no other checker, then set `shouldBeStable to `false`.
                if (--shouldBeStableCounter == 0) shouldBeStable = false
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Adding Validators:
    // /// Logic concerning increasing the number of `Validators`
    // /// required to validate their state at the barrier.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The root's child [ChildBarrier] within the root [NetSimBarrier].
     * The root does not provide [NetSimStabilizer]s, except the one passed to its sole child.
     *
     * Except for [RootNetSimStabilizer], all other [NetSimStabilizer] instances
     * operate on a [ChildBarrier], which can, in turn, contain additional
     * [ChildBarrier] instances, forming a hierarchical structure.
     */
    private val childBarrier: ChildBarrier = ChildBarrier(RootNetSimStabilizer())
    private val newInvalidatorMtx = Mutex()

    /**
     * @return a new [NetSimStabilizer] for this [NetSimBarrier].
     */
    internal suspend fun stabilizer(): NetSimStabilizer =
        newInvalidatorMtx.withLock {
            childBarrier.stabilizer()
        }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Base Stability Methods
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Suspends until the network is stable (All components
     * with an [NetSimStabilizer] validated their status).
     */
    internal suspend fun awaitStability() {
        stabilityMtx.lock()
        stabilityMtx.unlock()
    }

    /**
     * Executes [block] while the network should be stable,
     * following the [netSimStabilityMode] rules.
     *
     * @param netSimStabilityMode Specifies the stability guarantees while executing [block].
     * If not defined, it defaults to the mode inherited from [NetSimScope].
     * @param block The block that needs to be executed while the network is stable.
     */
    internal suspend fun <T> whileStable(
        netSimStabilityMode: NetSimStabilityMode = netSimConfig.stabilityMode,
        block: suspend () -> T,
    ): T =
        when (netSimStabilityMode) {
            NetSimStabilityMode.ENFORCED -> whileStabilityEnforced(block)
            NetSimStabilityMode.CHECKED -> whileStabilityChecked(block)
            NetSimStabilityMode.ASSUMED -> block()
        }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RootInvalidator
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * [NetSimStabilizer] passed to first [ChildBarrier] from the [NetSimBarrier].
     */
    private inner class RootNetSimStabilizer : NetSimStabilizer() {
        override val netSimConfig: NetSimConfig = this@NetSimBarrier.netSimConfig
        override val isValidated: Boolean
            get() = stabilityMtx.isLocked

        override suspend fun invalidate(): Unit = stabilityMtx.lock()

        override suspend fun validate(): Unit = stabilityMtx.unlock()

        override suspend fun <T> whileNetStable(
            netSimStabilityMode: NetSimStabilityMode,
            block: suspend () -> T,
        ): T = throw RuntimeException("should not be invoked")

        override suspend fun awaitStability() = throw RuntimeException("should not be invoked")
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ChildBarrier
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private inner class ChildBarrier(private val parentNetSimStabilizer: NetSimStabilizer) {
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Properties: counting invalidations at this barrier
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private var invalidCount: Int = 0
        private val countMtx = Mutex()

        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Properties: logic for constructing a new `NetSimStabilizer`
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private lateinit var childBarrier: ChildBarrier
        private var invalidatorCount: Int = 0
        private val invalidatorMax = netSimConfig.netSimDevConfig.barrierConfig.barrierNodeSize
        private val newInvalidatorMtx = Mutex()

        // Methods
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * @return a new [NetSimStabilizer].
         */
        suspend fun stabilizer(): NetSimStabilizer =
            newInvalidatorMtx.withLock {
                if (invalidatorCount < invalidatorMax) {
                    invalidatorCount++
                    NetSimStabilizerImpl()
                } else if (this::childBarrier.isInitialized) {
                    childBarrier.stabilizer()
                } else {
                    childBarrier = ChildBarrier(parentNetSimStabilizer = NetSimStabilizerImpl())
                    childBarrier.stabilizer()
                }
            }

        /**
         * @see NetSimStabilizer
         */
        private inner class NetSimStabilizerImpl : NetSimStabilizer() {
            override val netSimConfig: NetSimConfig = this@NetSimBarrier.netSimConfig

            /**
             * Current value of the validator.
             * - `true` => the owner component is in a stable state
             * - `false => the owner component is in an unstable state
             */
            override var isValidated: Boolean = true
                private set

            override suspend fun invalidate() {
                // If there is a `NetStabilityMode.CHECK` protected block that is being executed.
                if (shouldBeStable) throw NetSimStabilityException()

                // Wait until no `NetStabilityMode.ENFORCE` protected block is being executed.
                if (deter.value) {
                    deter.first { !it }
                }

                if (shouldBeStable) {
                    error(
                        "unable to invalidate network stability: " +
                            "a stability-checked block is currently being executed",
                    )
                }

                countMtx.withLock {
                    // If the owner component already invalidated its state, then return.
                    if (isValidated.not()) return
                    isValidated = false

                    // If this `ChildBarrier` was complete => parent was validated,
                    // then invalidate parent.
                    if (++invalidCount == 1) parentNetSimStabilizer.invalidate()
                }
            }

            override suspend fun validate() {
                countMtx.withLock {
                    // If the owner component already validated its state, then return.
                    if (isValidated) return
                    isValidated = true

                    // If this is the last `NetSimStabilizer` to validate its state
                    // in the `ChildBarrier` then validate `parentBarrier`
                    if (--invalidCount == 0) parentNetSimStabilizer.validate()
                }
            }

            override suspend fun <T> whileNetStable(
                netSimStabilityMode: NetSimStabilityMode,
                block: suspend () -> T,
            ): T = this@NetSimBarrier.whileStable(netSimStabilityMode, block)

            override suspend fun awaitStability() = this@NetSimBarrier.awaitStability()
        }
    }

    internal companion object Key : CoroutineContext.Key<NetSimBarrier>
}
