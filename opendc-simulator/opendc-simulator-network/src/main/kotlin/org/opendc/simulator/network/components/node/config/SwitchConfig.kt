package org.opendc.simulator.network.components.node.config

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.emodels.SwitchDfltEnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy

@Serializable
internal data class SwitchConfig(
    val defaultNPorts: Int? = null,
    val defaultPortSpeed: DataRate? = null,
    val defaultFairnessPolicy: FairnessPolicy? = null,
) {
    // TODO: make serializable
    val defaultEnModel: EnModel<Switch>? get() = SwitchDfltEnModel
}
