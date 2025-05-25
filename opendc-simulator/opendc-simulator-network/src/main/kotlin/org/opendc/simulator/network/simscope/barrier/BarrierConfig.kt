package org.opendc.simulator.network.simscope.barrier

import kotlinx.serialization.Serializable

@Serializable
internal data class BarrierConfig(
    val barrierNodeSize: Int = 50,
)
