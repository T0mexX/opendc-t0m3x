package org.opendc.simulator.network.utils.flyweight

import org.opendc.simulator.network.utils.Idx

internal interface IFlyWait<T: FlyWeight<T>>: FlyWeight<T> {
    val pool: FlyWeightPool<T>
    val poolIdx: Idx

    @Suppress("UNCHECKED_CAST")
    override suspend fun dispose() = pool.dispose(this as T)
}
