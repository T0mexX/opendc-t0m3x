package org.opendc.simulator.network.utils.flyweight


public fun interface FlyWeight<T: FlyWeight<T>> {
    public suspend fun dispose()
}
