package org.opendc.simulator.network.components.node.config

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeImpl
import org.opendc.simulator.network.components.node.NodeVersion
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTableV1
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTableVersion
import org.opendc.simulator.network.components.port.PortConfig
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed

@Serializable
internal data class NodeConfig(
    val version: NodeVersion = NodeImpl,
    val flowTableVersion: FlowTableVersion = FlowTableV1,
    val portConfig: PortConfig = PortConfig(),
    val hostNodeConfig: HostNodeConfig = HostNodeConfig(),
    val switchConfig: SwitchConfig = SwitchConfig(),
    val coreSwitchConfig: CoreSwitchConfig = CoreSwitchConfig(),
    val defaultNPorts: Int? = null,
    val defaultPortSpeed: DataRate? = null,
    val defaultFairnessPolicy: FairnessPolicy = FirstComeFirstServed(),
) {
    val defaultEnModel: EnModel<Node<*>> get() =
        throw IllegalStateException("No default generic node energy model")
}
