package org.opendc.simulator.network.components.node

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

/**
 * TODO
 */
internal class HostNode private constructor(
    id: NodeId,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var fairnessPolicy: FairnessPolicy,
    override var routPolicy: RoutPolicy,
    override var enModel: EnModel<HostNode>,
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
) : SenderNode<HostNode>(id), SerializableNode {

    fun bo() {
        enModel.computeCurrConsumpt(this)
    }

    override lateinit var ports: List<Port>

    override fun toSpecs(): Specs<HostNode> =
        HostNodeSpecs(
            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
            fairnessPolicy = fairnessPolicy,
        )

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            id: NodeId? = null,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            fairnessPolicy: FairnessPolicy? = null,
            enModel: EnModel<HostNode>? = null,
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
                    ?: nodeConfig.defaultFairnessPolicy,
                routPolicy = this@NetSimScope.config.routPolicy,
                enModel = enModel
                    ?:hostConfig.defaultEnModel
                    ?:nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { h ->
                h.ports = 0.rangeUntil(h.nPorts).map { idx -> nodeConfig.portConfig.version(h, idx) }
            }
        }
    }
}
