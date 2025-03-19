package org.opendc.simulator.network.components.node.coreswitch

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy

@Serializable
internal data class CoreSwitchConfig(
    val defaultNPorts: Int? = null,
    val defaultPortSpeed: DataRate? = null,
    val defaultFairnessPolicy: FairnessPolicy? = null,
    val defaultRoutingPolicy: RoutingPolicy? = null,
)
