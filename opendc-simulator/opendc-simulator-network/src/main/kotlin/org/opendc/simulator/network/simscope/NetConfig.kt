package org.opendc.simulator.network.simscope

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.Network

/**
 * Contains configuration for a specific [Network] about
 * possible optimizations during the building process, for routing etc.,
 * that do not inherently belong to the topology specification.
 */
@Serializable
@SerialName("netConfig")
internal data class NetConfig(
    @SerialName("includeRoutInfo2Switches") val swRout: Boolean = false,
    @SerialName("subnetOptimization") val subnetOpt: Boolean = true,
)
