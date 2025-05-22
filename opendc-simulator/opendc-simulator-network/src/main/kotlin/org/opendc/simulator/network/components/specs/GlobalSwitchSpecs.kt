package org.opendc.simulator.network.components.specs

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
@Serializable
@SerialName("global-switch")
internal data class GlobalSwitchSpecs(
//    @Contextual val ip: IPv4Address? = null,
    private val portSpeed: DataRate? = null,
    private val nPorts: Int? = null,
): NodeSpecs<GlobalSwitch> {

    context(NetSimScope)
    override fun nPorts(): Int =
        nPorts
            ?: devConfig.nodeConfig.coreSwitchConfig.defaultNPorts
            ?: devConfig.nodeConfig.switchConfig.defaultNPorts
            ?: devConfig.nodeConfig.defaultNPorts!!

    context(NetSimScope)
    override fun portSpeed(): DataRate =
        portSpeed
            ?: devConfig.nodeConfig.coreSwitchConfig.defaultPortSpeed
            ?: devConfig.nodeConfig.switchConfig.defaultPortSpeed
            ?: devConfig.nodeConfig.defaultPortSpeed!!

    context(NetSimScope)
    override suspend fun build(): GlobalSwitch =
        GlobalSwitch(
//            addr = ip,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    fun toSwitchSpecs(): SwitchSpecs =
        SwitchSpecs(
//            ip = ip,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun buildAsCore(internet: Internet, updtRoutTbl: Boolean = true): GlobalSwitch =
        this.copy(
            nPorts = (
                nPorts
                ?: devConfig.nodeConfig.coreSwitchConfig.defaultNPorts
                ?: devConfig.nodeConfig.defaultNPorts!!
                ) + 1
        ).build().also {
            it.invalidate()
            it.msgSyncConnect(internet, updtRoutTbl = updtRoutTbl)
        }

    context(NetSimScope)
    suspend fun build(subnet: IPv4Address? = null): GlobalSwitch =
        GlobalSwitch(
            portSpeed = portSpeed,
            nPorts = nPorts,
            subnet = subnet,
        )

    context(NetSimScope)
    suspend fun buildAsCore(internet: Internet, updtRoutTbl: Boolean = true, subnet: IPv4Address? = null): GlobalSwitch =
        this.copy(
            nPorts = (
                nPorts
                    ?: devConfig.nodeConfig.coreSwitchConfig.defaultNPorts
                    ?: devConfig.nodeConfig.defaultNPorts!!
                ) + 1
        ).build(subnet = subnet).also {
            it.invalidate()
            it.msgSyncConnect(internet, updtRoutTbl = updtRoutTbl)
        }
}
