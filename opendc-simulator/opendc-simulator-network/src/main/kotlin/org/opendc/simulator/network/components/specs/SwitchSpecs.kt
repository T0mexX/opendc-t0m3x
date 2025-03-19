package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("witch-specs")
internal data class SwitchSpecs(
    val id: NodeId? = null,
    val portSpeed: DataRate? = null,
    val numOfPorts: Int? = null,
    val fairnessPolicy: FairnessPolicy?,
    val portSelectionPolicy: RoutingPolicy?,
): Specs<Switch> {
    context(NetSimScope)
    override suspend fun build(): Switch =
        Switch(
            id = id,
            portSpeed = portSpeed,
            nPorts = numOfPorts,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )

    fun toCoreSwitchSpecs(): CoreSwitchSpecs =
        CoreSwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = numOfPorts,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )
}
