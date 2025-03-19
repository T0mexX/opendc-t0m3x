package org.opendc.simulator.network.components.networks

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeV0
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.policies.forwarding.ECMP
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal class Internet(
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
): NodeV0(NetworkV0.INTERNET_ID), SenderNode {

    override val portSpeed: DataRate = DataRate.max
    override var nPorts: Int = 0
    override var fairnessPolicy: FairnessPolicy = MaxMinPerPort
    override var portSelectionPolicy: RoutingPolicy = ECMP

    override fun startFlow(netflow: NetFlow) {
        TODO("Not yet implemented")
    }

    override fun stopFlow(netFlow: NetFlow) {
        TODO("Not yet implemented")
    }

    override val ports: List<Port> get() = _ports
    private val _ports: MutableList<Port> = mutableListOf()

    override fun toSpecs(): Specs<Node> {
        throw RuntimeException("Internet does not have specs")
    }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(): Internet =
            Internet(
                flowTable = devConfig.nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer(),
            )
    }
}
