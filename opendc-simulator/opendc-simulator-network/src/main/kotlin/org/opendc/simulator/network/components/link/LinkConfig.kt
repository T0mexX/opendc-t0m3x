package org.opendc.simulator.network.components.link

import kotlinx.serialization.Serializable

@Serializable
internal data class LinkConfig(
    val initialCapacity: Int = 10
)
