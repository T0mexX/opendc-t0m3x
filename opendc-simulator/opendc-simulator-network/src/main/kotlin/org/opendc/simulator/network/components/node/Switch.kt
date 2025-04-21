package org.opendc.simulator.network.components.node

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal open class Switch protected constructor(
    id: NodeId,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var fairnessPolicy: FairnessPolicy,
    override var routPolicy: RoutPolicy,
    override val enModel: EnModel<Switch>,
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
): NodeImpl<Switch>(id), EnConsumer<Switch>, SerializableNode {

    override lateinit var  ports: List<Port>

    override fun toSpecs(): Specs<Switch> =
        SwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
        )

    companion object {
        context(NetSimScope)
        internal suspend operator fun invoke(
            id: NodeId? = null,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            fairnessPolicy: FairnessPolicy? = null,
            enModel: EnModel<Switch>? = null,
        ): Switch {
            val nodeConfig = devConfig.nodeConfig
            val switchConfig = nodeConfig.switchConfig
            return Switch(
                id = id ?: idDispenser.getNodeId(),
                portSpeed = portSpeed
                    ?: switchConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: switchConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                fairnessPolicy = fairnessPolicy
                    ?: switchConfig.defaultFairnessPolicy
                    ?: nodeConfig.defaultFairnessPolicy,
                routPolicy = this@NetSimScope.config.routPolicy,
                enModel = enModel
                    ?:switchConfig.defaultEnModel
                    ?:nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { s ->
                s.ports = 0.rangeUntil(s.nPorts).map { idx ->
                    with(this@NetSimScope) {
                        nodeConfig.portConfig.version(s, idx)
                    }
                }
            }
        }
    }
}
