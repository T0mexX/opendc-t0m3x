package org.opendc.simulator.network.components.node.host

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.NodeV0
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.components.specs.WithSpecs
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal class HostNode private constructor(
    id: NodeId,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var fairnessPolicy: FairnessPolicy,
    override var portSelectionPolicy: RoutingPolicy,
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
) : NodeV0(id), SenderNode {

    override lateinit var ports: List<Port>

    override fun toSpecs(): Specs<HostNode> =
        HostNodeSpecs(
            id = id,
            portSpeed = portSpeed,
            numOfPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            id: NodeId? = null,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            fairnessPolicy: FairnessPolicy? = null,
            portSelectionPolicy: RoutingPolicy? = null,
        ): HostNode {
            val nodeConfig = devConfig.nodeConfig
            val hostConfig = nodeConfig.hostNodeConfig
            return HostNode(
                id = id ?: idDispenser.getNodeId(),
                portSpeed = portSpeed
                    ?: hostConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: hostConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                fairnessPolicy = fairnessPolicy
                    ?: hostConfig.defaultFairnessPolicy
                    ?: nodeConfig.defaultFairnessPolicy!!,
                portSelectionPolicy = portSelectionPolicy
                    ?: hostConfig.defaultRoutingPolicy
                    ?: nodeConfig.defaultRoutingPolicy!!,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { h ->
                h.ports = 0.rangeUntil(h.nPorts).map { idx -> nodeConfig.portConfig.version(h, idx) }
            }
        }
    }
}
