package org.opendc.simulator.network.repl.synthetic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * Traffic pattern where each terminal is sending and receiving to/from every other terminal.
 */
@Serializable
@SerialName("full")
internal data object Full: SyntheticWl<Network> {
    context(NetSimScope)
    override suspend fun startSyntheticFlows(net: Network, demandMapping: (HostNode) -> DataRate) {
        val hosts = net.getNodesById<HostNode>().values

        // Start flows so that each host sends and receives to/from any other host.
        hosts.forEach { h1 ->
            hosts.forEach inner@ { h2 ->
                if (h1 == h2) return@inner
                net.startFlow(
                    devConfig.netFlowConfig.version(
                        senderId = h1.id,
                        destId = h2.id,
                        demand = demandMapping(h1) / (hosts.size - 1)
                    )
                )
            }
        }
    }
}
