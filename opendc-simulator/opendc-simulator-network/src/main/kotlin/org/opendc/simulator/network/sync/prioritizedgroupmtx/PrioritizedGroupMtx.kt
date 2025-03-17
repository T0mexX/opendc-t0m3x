package org.opendc.simulator.network.sync.prioritizedgroupmtx

/**
 *
 */
internal abstract class PrioritizedGroupMtx {
    internal abstract suspend fun <T> withPrioritizedLock(block: suspend () -> T): T

    internal abstract suspend fun <T> withNonPrioritizedLock(block: suspend () -> T): T

    internal abstract suspend fun prioritizedLock()
    internal abstract suspend fun prioritizedUnlock()

    internal abstract suspend fun nonPrioritizedLock()
    internal abstract suspend fun nonPrioritizedUnlock()

    companion object {
        val HIGH_PRIORITY = object {}
        val LOW_PRIORITY = object {}
    }
}
