package org.opendc.simulator.network.components.specs

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.Contextual
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
    @Contextual val ip: IPv4Address? = null,
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
    override suspend fun build(): HostNode {
        // If ip is specified in specs, then claim it.
        ip?.let { addrMngr.claimIp(it) }

        return HostNode(
            ip = ip,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )
    }

    context(NetSimScope)
    suspend fun build(subnet: IPv4Address): HostNode {
        // If ip is specified in specs, then claim it.
        ip?.let { addrMngr.claimIp(it) }

        return HostNode(
            ip = ip,
            portSpeed = portSpeed,
            nPorts = nPorts,
            subnet = subnet,
        )
    }
}

