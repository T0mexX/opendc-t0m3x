package org.opendc.simulator.network.components.node

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.specs.CoreSwitchSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.energy.EnModel
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
    enModel: EnModel<Switch>,
    flowTable: FlowTable,
    stabilizer: NetSimStabilizer,
) : Switch(id, portSpeed, nPorts, fairnessPolicy, portSelectionPolicy, enModel, flowTable, stabilizer), SerializableNode {

    override fun toSpecs(): Specs<CoreSwitch> =
        CoreSwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = routingPolicy,
        )


    companion object {
        context(NetSimScope)
        internal suspend operator fun invoke(
            id: NodeId? = null,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            fairnessPolicy: FairnessPolicy? = null,
            portSelectionPolicy: RoutingPolicy? = null,
            enModel: EnModel<Switch>? = null,
        ): CoreSwitch {
            val nodeConfig = devConfig.nodeConfig
            val switchConfig = devConfig.nodeConfig.switchConfig
            val coreSwitchConfig = nodeConfig.coreSwitchConfig
            return CoreSwitch(
                id = id ?: idDispenser.getNodeId(),
                portSpeed = portSpeed
                    ?: coreSwitchConfig.defaultPortSpeed
                    ?: switchConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: coreSwitchConfig.defaultNPorts
                    ?: switchConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                fairnessPolicy = fairnessPolicy
                    ?: coreSwitchConfig.defaultFairnessPolicy
                    ?: switchConfig.defaultFairnessPolicy
                    ?: nodeConfig.defaultFairnessPolicy,
                portSelectionPolicy = portSelectionPolicy
                    ?: coreSwitchConfig.defaultRoutingPolicy
                    ?: switchConfig.defaultRoutingPolicy
                    ?: nodeConfig.defaultRoutingPolicy,
                enModel = enModel
                    ?: coreSwitchConfig.defaultEnModel
                    ?: switchConfig.defaultEnModel
                    ?: nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { cs ->
                cs.ports = 0.rangeUntil(cs.nPorts).map { idx -> nodeConfig.portConfig.version(cs, idx) }
            }
        }
    }
}
