package org.opendc.simulator.network.utils.flyweight.publics


public fun interface FW<out T: FW<T>> {
    public suspend fun dispose()
}
