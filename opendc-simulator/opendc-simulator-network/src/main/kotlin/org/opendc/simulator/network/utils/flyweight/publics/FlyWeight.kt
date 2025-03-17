package org.opendc.simulator.network.utils.flyweight.publics


public fun interface FlyWeight<in T: FlyWeight<T>> {
    public suspend fun dispose()
}
