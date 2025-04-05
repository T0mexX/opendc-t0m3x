package org.opendc.simulator.network.utils.flyweight.internals

import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId

internal interface IFW<out T: FW<T>>: FW<T> {
    val pool: FWPool<T, FWId<T>>
    val poolIdx: Idx

    @Suppress("UNCHECKED_CAST")
    override suspend fun dispose() = pool.dispose(this as T)
}
