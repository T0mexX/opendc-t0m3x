package org.opendc.simulator.network.simscope

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.config.NodeConfig
import org.opendc.simulator.network.components.port.PortConfig
import org.opendc.simulator.network.flow.internals.NetFlowConfig
import org.opendc.simulator.network.utils.flyweight.internals.FWConfig

@Serializable
internal data class NetSimDevConfig(
    val flyWeightConfig: FWConfig = FWConfig(),
    val netFlowConfig: NetFlowConfig = NetFlowConfig(),
    val portConfig: PortConfig = PortConfig(),
    val nodeConfig: NodeConfig = NodeConfig(),
)
