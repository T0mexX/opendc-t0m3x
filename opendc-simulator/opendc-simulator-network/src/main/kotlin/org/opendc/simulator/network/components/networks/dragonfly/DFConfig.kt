package org.opendc.simulator.network.components.networks.dragonfly

import kotlinx.serialization.Serializable

/**
 * TODO
 * @property subnets Determines if each group receives its own subnets (each node
 * not in subnet S will treat S as a single entry for routing decision, reducing the space utilization).
 * Setting this option to `true` may cause some non-optimal paths to be used,
 * but it enables simulations with high number of nodes.
 */
@Serializable
internal data class DFConfig(
    val subnets: Boolean = true,
)
