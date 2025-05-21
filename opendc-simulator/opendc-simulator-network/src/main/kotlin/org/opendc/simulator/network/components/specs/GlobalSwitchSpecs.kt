package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
@Serializable
@SerialName("global-switch")
internal data class GlobalSwitchSpecs(
    val id: NodeId? = null,
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
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    fun toSwitchSpecs(): SwitchSpecs =
        SwitchSpecs(
            id = id,
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
}
