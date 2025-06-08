package org.opendc.simulator.network.utils.flyweight.internals

import org.opendc.simulator.network.utils.flyweight.publics.FW

internal interface FWDispenser<T: FW<T>> {
    /**
     * TODO
     * @param block Can be used to initialize the [FW] object in a suspending context.
     */
    suspend fun acquire(block: (suspend T.() -> Unit)? = null): T
}
