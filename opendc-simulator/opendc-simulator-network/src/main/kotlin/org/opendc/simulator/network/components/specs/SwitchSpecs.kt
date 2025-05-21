package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("switch")
internal data class SwitchSpecs(
    val id: NodeId? = null,
    private val portSpeed: DataRate? = null,
    private val nPorts: Int? = null,
): NodeSpecs<Switch> {
    context(NetSimScope)
    override fun nPorts(): Int =
        nPorts
            ?: devConfig.nodeConfig.switchConfig.defaultNPorts
            ?: devConfig.nodeConfig.defaultNPorts!!

    context(NetSimScope)
    override fun portSpeed(): DataRate =
        portSpeed
            ?: devConfig.nodeConfig.switchConfig.defaultPortSpeed
            ?: devConfig.nodeConfig.defaultPortSpeed!!

    context(NetSimScope)
    override suspend fun build(): Switch =
        Switch(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    fun toGlobalSwitchSpecs(): GlobalSwitchSpecs =
        GlobalSwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )
}
