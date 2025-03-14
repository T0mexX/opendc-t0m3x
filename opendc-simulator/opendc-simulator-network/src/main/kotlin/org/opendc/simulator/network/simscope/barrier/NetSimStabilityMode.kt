package org.opendc.simulator.network.simscope.barrier

/**
 * Determines how to handle block that should
 * be executed while the network is stable.
 */
public enum class NetSimStabilityMode {
    /**
     * - network is awaited to be stable.
     * - network is locked in stable state.
     * - block is executed.
     * - network is unlocked.
     */
    ENFORCED,

    /**
     * If any invalidation is tried while the block is being executed,
     * the coroutine trying to invalidate its state will throw an exception.
     */
    CHECKED,

    /**
     * Correct stability handling is assumed
     * and block is simply executed.
     */
    ASSUMED
}
