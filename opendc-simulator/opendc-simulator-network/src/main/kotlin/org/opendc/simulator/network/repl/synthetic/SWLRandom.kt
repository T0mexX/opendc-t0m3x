package org.opendc.simulator.network.repl.synthetic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.increaseMax

/**
 * Synthetic traffic where each host sends to another random host.
 */
@Serializable
@SerialName("random")
internal data object SWLRandom: SyntheticWl<Network> {
    context(NetSimScope, ProgressBar)
    override suspend fun startSyntheticFlows(
        net: Network,
        demandMapping: (HostNode) -> DataRate
    ) {
        val hosts = net.getNodesById<HostNode>().values

        // Increase the number of actions to be taken to complete the current context
        // progress bar by the number of flows that will need to be started.
        this@ProgressBar.increaseMax(hosts.size.toLong())

        val random = this@NetSimScope.config.random

        hosts.forEach { sender ->
            //
            // Get a random host in network different from `sender`.
            var dest: HostNode
            do {
                dest = hosts.random(random)
            } while (dest === sender)

            // Start the flow.
            net.startFlow(
                this@NetSimScope.devConfig.netFlowConfig.version(
                    senderId = sender.id,
                    destId = dest.id,
                    demand = demandMapping(sender),
                )
            )

            this@ProgressBar.step()
        }
    }
}

