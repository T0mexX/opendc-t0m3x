package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("switch-specs")
internal data class SwitchSpecs(
    val id: NodeId? = null,
    private val portSpeed: DataRate? = null,
    private val nPorts: Int? = null,
    private val fairnessPolicy: FairnessPolicy? = null,
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
    override fun fairnessPolicy(): FairnessPolicy =
        fairnessPolicy
            ?: devConfig.nodeConfig.switchConfig.defaultFairnessPolicy
            ?: devConfig.nodeConfig.defaultFairnessPolicy

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
