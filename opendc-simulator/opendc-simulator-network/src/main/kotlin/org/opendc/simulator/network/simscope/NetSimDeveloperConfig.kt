package org.opendc.simulator.network.simscope

import kotlinx.serialization.Serializable

@Serializable
public data class NetSimDeveloperConfig(
    val nSubPools: Int = 10
)
