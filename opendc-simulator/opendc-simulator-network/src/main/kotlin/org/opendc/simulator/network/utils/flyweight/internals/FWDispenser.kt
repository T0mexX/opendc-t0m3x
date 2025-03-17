package org.opendc.simulator.network.utils.flyweight.internals

import org.opendc.simulator.network.utils.flyweight.publics.FlyWeight

internal fun interface FWDispenser<in T: FlyWeight<in T>> {
    suspend fun acquire(): @UnsafeVariance T
}
