package org.opendc.simulator.network.components.node.config

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.emodels.HostNodeDfltEnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy

@Serializable
internal data class HostNodeConfig(
    val defaultNPorts: Int? = null,
    val defaultPortSpeed: DataRate? = null,
) {
    // TODO: make serializable
    val defaultEnModel: EnModel<HostNode>? get() = HostNodeDfltEnModel
}
