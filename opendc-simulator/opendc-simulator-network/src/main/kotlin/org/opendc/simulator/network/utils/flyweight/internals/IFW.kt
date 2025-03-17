package org.opendc.simulator.network.utils.flyweight.internals

import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeight

internal interface IFW<T: FlyWeight<T>>: FlyWeight<T> {
    val pool: FWPool<T>
    val poolIdx: Idx

    @Suppress("UNCHECKED_CAST")
    override suspend fun dispose() = pool.dispose(this as T)
}
