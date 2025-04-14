package org.opendc.simulator.network.utils.sync

import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal interface Synchronizable<Self: Synchronizable<Self>> {
    /**
     * TODO
     */
    val lastSync: Timestamp

    /**
     * TODO
     * If no force update and last sync in same virtual timestamp then no update performed.
     */
    context(NetSimScope)
    suspend fun sync(forceUpdt: Boolean = false): Self

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun isSync(): Boolean = lastSync approx tmSrc.tmstamp
}
