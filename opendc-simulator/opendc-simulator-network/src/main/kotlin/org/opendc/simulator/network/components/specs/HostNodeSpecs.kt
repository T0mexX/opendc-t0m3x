package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("host")
internal data class HostNodeSpecs(
    val id: NodeId? = null,
    private val portSpeed: DataRate? = null,
    private val nPorts: Int? = null,
) : NodeSpecs<HostNode> {

    context(NetSimScope)
    override fun nPorts(): Int =
        nPorts
            ?: devConfig.nodeConfig.hostNodeConfig.defaultNPorts
            ?: devConfig.nodeConfig.defaultNPorts!!

    context(NetSimScope)
    override fun portSpeed(): DataRate =
        portSpeed
            ?: devConfig.nodeConfig.hostNodeConfig.defaultPortSpeed
            ?: devConfig.nodeConfig.defaultPortSpeed!!

    context(NetSimScope)
    override suspend fun build(): HostNode =
        HostNode(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )
}

