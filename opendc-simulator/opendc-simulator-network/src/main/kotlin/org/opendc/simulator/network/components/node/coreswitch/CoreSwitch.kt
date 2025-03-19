package org.opendc.simulator.network.components.node.coreswitch

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.specs.CoreSwitchSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal class CoreSwitch private constructor(
    id: NodeId,
    portSpeed: DataRate,
    nPorts: Int,
    fairnessPolicy: FairnessPolicy,
    portSelectionPolicy: RoutingPolicy,
    flowTable: FlowTable,
    stabilizer: NetSimStabilizer,
) : Switch(id, portSpeed, nPorts, fairnessPolicy, portSelectionPolicy, flowTable, stabilizer) {

    override fun toSpecs(): Specs<CoreSwitch> =
        CoreSwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )



    companion object {
        context(NetSimScope)
        internal suspend operator fun invoke(
            id: NodeId? = null,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            fairnessPolicy: FairnessPolicy? = null,
            portSelectionPolicy: RoutingPolicy? = null,
        ): CoreSwitch {
            val nodeConfig = devConfig.nodeConfig
            val coreSwitchConfig = nodeConfig.coreSwitchConfig
            return CoreSwitch(
                id = id ?: idDispenser.getNodeId(),
                portSpeed = portSpeed
                    ?: coreSwitchConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: coreSwitchConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                fairnessPolicy = fairnessPolicy
                    ?: coreSwitchConfig.defaultFairnessPolicy
                    ?: nodeConfig.defaultFairnessPolicy!!,
                portSelectionPolicy = portSelectionPolicy
                    ?: coreSwitchConfig.defaultRoutingPolicy
                    ?: nodeConfig.defaultRoutingPolicy!!,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { cs ->
                cs.ports = 0.rangeUntil(cs.nPorts).map { idx -> nodeConfig.portConfig.version(cs, idx) }
            }
        }
    }
}
