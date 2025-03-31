package org.opendc.simulator.network.utils.flyweight.internals

import org.opendc.simulator.network.utils.flyweight.publics.FW

internal fun interface FWDispenser<in T: FW<in T>> {
    suspend fun acquire(): @UnsafeVariance T
}
