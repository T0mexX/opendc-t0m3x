package org.opendc.simulator.network.simscope

import kotlinx.serialization.Serializable

@Serializable
public data class NetSimDevConfig(
    val nSubPools: Int = 10,
    val startingListsSize: Int = 100
)
