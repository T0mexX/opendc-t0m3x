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
     */
    context(NetSimScope)
    suspend fun sync(): Self

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun isSync(): Boolean = lastSync approx tmSrc.tmstamp
}
