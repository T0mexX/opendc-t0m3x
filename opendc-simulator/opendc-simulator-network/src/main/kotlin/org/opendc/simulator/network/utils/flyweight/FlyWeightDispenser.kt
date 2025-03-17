package org.opendc.simulator.network.utils.flyweight

internal fun interface FlyWeightDispenser<T: org.opendc.simulator.network.utils.flyweight.FlyWeight<T>> {
    fun acquire(): T
}
