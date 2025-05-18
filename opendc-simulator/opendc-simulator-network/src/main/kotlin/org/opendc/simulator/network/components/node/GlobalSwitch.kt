package org.opendc.simulator.network.components.node

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.specs.GlobalSwitchSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal class GlobalSwitch private constructor(
    id: NodeId,
    portSpeed: DataRate,
    nPorts: Int,
    fairnessPolicy: FairnessPolicy,
    routPolicy: RoutPolicy,
    enModel: EnModel<Switch>,
    flowTable: FlowTable,
    stabilizer: NetSimStabilizer,
) : Switch(id, portSpeed, nPorts, fairnessPolicy, routPolicy, enModel, flowTable, stabilizer), SerializableNode {

    override fun toSpecs(): Specs<GlobalSwitch> =
        GlobalSwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )


    companion object {
        context(NetSimScope)
        internal suspend operator fun invoke(
            id: NodeId? = null,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            enModel: EnModel<Switch>? = null,
        ): GlobalSwitch {
            val nodeConfig = devConfig.nodeConfig
            val switchConfig = devConfig.nodeConfig.switchConfig
            val coreSwitchConfig = nodeConfig.coreSwitchConfig
            return GlobalSwitch(
                id = id ?: idDispenser.getNodeId(),
                portSpeed = portSpeed
                    ?: coreSwitchConfig.defaultPortSpeed
                    ?: switchConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: coreSwitchConfig.defaultNPorts
                    ?: switchConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                fairnessPolicy = this@NetSimScope.config.fairPolicy,
                routPolicy = this@NetSimScope.config.routPolicy,
                enModel = enModel
                    ?: coreSwitchConfig.defaultEnModel
                    ?: switchConfig.defaultEnModel
                    ?: nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { cs ->
                cs.ports = 0.rangeUntil(cs.nPorts).map { idx -> nodeConfig.portConfig.version(cs, idx) }
                cs.invalidate()
                cs.netLaunch()
            }
        }
    }
}
