package org.opendc.simulator.network.components.networks.clos

import kotlinx.serialization.Serializable

/**
 * TODO
 */
@Serializable
internal data class ClosConfig(
    // TODO: change name.
    // TODO: make use of it.
    val updtRoutingOnEachConnect: Boolean = false,
)
