package org.opendc.simulator.network.simscope

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.clos.ClosConfig
import org.opendc.simulator.network.components.networks.dragonfly.DFConfig
import org.opendc.simulator.network.components.networks.ftree.FTreeConfig

/**
 * Contains configuration for a specific [Network] about
 * possible optimizations during the building process, for routing etc.,
 * that do not inherently belong to the topology specification.
 */
@Serializable
@SerialName("netConfig")
internal data class NetConfig(
    // TODO: change name.
    // TODO: currently unused.
    // TODO: write that it is needed for some routing algorithms.
    val includeRoutInfo2Switches: Boolean = false,
    val ftreeConfig: FTreeConfig = FTreeConfig(),
    val dfConfig: DFConfig = DFConfig(),
    val closConfig: ClosConfig = ClosConfig(),
)
