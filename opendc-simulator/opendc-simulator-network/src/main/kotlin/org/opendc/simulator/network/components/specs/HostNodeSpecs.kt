package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.host.HostNode
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("host-node-specs")
internal data class HostNodeSpecs(
    val id: NodeId? = null,
    val portSpeed: DataRate? = null,
    val numOfPorts: Int? = null,
    val fairnessPolicy: FairnessPolicy?,
    val portSelectionPolicy: RoutingPolicy?,
) : Specs<HostNode> {
    context(NetSimScope)
    override suspend fun build(): HostNode =
        HostNode(
            id = id,
            portSpeed = portSpeed,
            nPorts = numOfPorts,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )
}

