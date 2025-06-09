package org.opendc.simulator.network.simscope

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.link.LinkConfig
import org.opendc.simulator.network.components.node.config.NodeConfig
import org.opendc.simulator.network.flow.internals.NetFlowConfig
import org.opendc.simulator.network.simscope.barrier.BarrierConfig
import org.opendc.simulator.network.utils.flyweight.internals.FWConfig

@Serializable
internal data class NetSimDevConfig(
    val flyWeightConfig: FWConfig = FWConfig(),
    val netFlowConfig: NetFlowConfig = NetFlowConfig(),
    val nodeConfig: NodeConfig = NodeConfig(),
    val netConfig: NetConfig = NetConfig(),
    val linkConfig: LinkConfig = LinkConfig(),
    val barrierConfig: BarrierConfig = BarrierConfig(),
)
