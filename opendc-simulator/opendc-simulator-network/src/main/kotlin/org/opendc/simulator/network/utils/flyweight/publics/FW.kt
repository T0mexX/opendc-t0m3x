package org.opendc.simulator.network.utils.flyweight.publics


public fun interface FW<in T: FW<T>> {
    public suspend fun dispose()
}
