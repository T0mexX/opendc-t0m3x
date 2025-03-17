package org.opendc.simulator.network.utils.flyweight.internals

import kotlinx.serialization.Serializable

@Serializable
internal data class FWConfig(
    val nSubPools: Int = 10,
    val initialCapacity: Int = 100,
)
