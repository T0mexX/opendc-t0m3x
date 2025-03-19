package org.opendc.simulator.network.components.node.switchh

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.NodeV0
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal open class Switch protected constructor(
    id: NodeId,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var fairnessPolicy: FairnessPolicy,
    override var portSelectionPolicy: RoutingPolicy,
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
): NodeV0(id) {

    override lateinit var  ports: List<Port>

    override fun toSpecs(): Specs<Switch> =
        SwitchSpecs(
            id = id,
            portSpeed = portSpeed,
            numOfPorts = nPorts,
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
                    ?: nodeConfig.defaultFairnessPolicy!!,
                portSelectionPolicy = portSelectionPolicy
                    ?: switchConfig.defaultRoutingPolicy
                    ?: nodeConfig.defaultRoutingPolicy!!,
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
