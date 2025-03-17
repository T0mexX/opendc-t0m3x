package org.opendc.simulator.network.utils.flyweight

internal fun interface FlyWeightDispenser<in T: FlyWeight<in T>> {
    suspend fun acquire(): @UnsafeVariance T
}
