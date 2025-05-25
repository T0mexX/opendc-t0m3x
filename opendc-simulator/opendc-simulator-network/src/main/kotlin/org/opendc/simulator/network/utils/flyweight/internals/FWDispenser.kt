package org.opendc.simulator.network.utils.flyweight.internals

import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.flyweight.publics.FW

internal fun interface FWDispenser<out T: FW<T>> {
    suspend fun acquire(): T
}
