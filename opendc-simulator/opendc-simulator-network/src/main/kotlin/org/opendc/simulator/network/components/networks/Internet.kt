package org.opendc.simulator.network.components.networks

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortV1
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.forwarding.RoutingPolicy
import org.opendc.simulator.network.policies.forwarding.ECMP
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal class Internet(
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
): SenderNode(INTERNET_ID) {

    override val portSpeed: DataRate = DataRate.max
    override var nPorts: Int = 0
    override var fairnessPolicy: FairnessPolicy = FirstComeFirstServed
    override var routingPolicy: RoutingPolicy = ECMP

    override val ports: List<Port> get() = _ports
    private val _ports: MutableList<Port> = mutableListOf()

    context(NetSimScope)
    override suspend fun getFreePort(): Port =
        ports.firstOrNull {
            it.txLink == null
        } ?: let {
            _ports.add(PortV1(owner = this, portIdx = ports.size))
            nPorts += 1
            ports.last().also { it.netLaunch() }
        }

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
