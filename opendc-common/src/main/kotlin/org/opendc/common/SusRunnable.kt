package org.opendc.common

/**
 * Same as [Runnable] but suspending.
 */
public fun interface SusRunnable {
    public suspend fun susRun()
}
