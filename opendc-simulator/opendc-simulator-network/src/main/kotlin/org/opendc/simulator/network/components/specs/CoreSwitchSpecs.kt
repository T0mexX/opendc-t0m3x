package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.CoreSwitch
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("core-switch-specs")
internal data class CoreSwitchSpecs(
    val id: NodeId? = null,
    val portSpeed: DataRate? = null,
    val nPorts: Int? = null,
    val fairnessPolicy: FairnessPolicy? = null,
): Specs<CoreSwitch> {
    context(NetSimScope)
    override suspend fun build(): CoreSwitch =
        CoreSwitch(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
        )

    fun toSwitchSpecs(): SwitchSpecs =
        SwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
        )

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun buildAsCore(internet: Internet): CoreSwitch =
        this.copy(
            nPorts = (
                nPorts
                ?: devConfig.nodeConfig.coreSwitchConfig.defaultNPorts
                ?: devConfig.nodeConfig.defaultNPorts!!
                ) + 1
        ).build().also {
            it.netLaunch()
            it.connectTo(internet)
        }
}
