package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("switch-specs")
internal data class SwitchSpecs(
    val id: NodeId? = null,
    val portSpeed: DataRate? = null,
    val nPorts: Int? = null,
    val fairnessPolicy: FairnessPolicy? = null,
): Specs<Switch> {
    context(NetSimScope)
    override suspend fun build(): Switch =
        Switch(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
        )

    fun toCoreSwitchSpecs(): CoreSwitchSpecs =
        CoreSwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
        )
}
