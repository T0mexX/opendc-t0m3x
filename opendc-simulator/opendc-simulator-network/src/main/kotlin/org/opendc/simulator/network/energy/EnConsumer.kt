package org.opendc.simulator.network.energy

import org.opendc.common.units.Power

/**
 * TODO
 * [EnConsumer] of [HostNode] is a subtype of energy consumer of [Node].
 * When an enConsumer of node is needed, one of hostname can be passed.
 */
internal interface EnConsumer<out T: EnConsumer<T>> {
    val enModel: EnModel<@UnsafeVariance T>

    @Suppress("UNCHECKED_CAST")
    fun computePwrDraw(): Power = enModel.computeCurrConsumpt(this as T)
}
