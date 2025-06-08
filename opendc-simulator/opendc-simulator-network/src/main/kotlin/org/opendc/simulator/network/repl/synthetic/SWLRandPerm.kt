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
 * Differs from [SWLRandom] in the fact that no 2 hosts can be sending to the same endpoint.
 */
@Serializable
@SerialName("random-permutation")
internal object SWLRandPerm: SyntheticWl<Network> {
    context(NetSimScope, ProgressBar) override suspend fun startSyntheticFlows(
        net: Network,
        demandMapping: (HostNode) -> DataRate
    ) {
        val hosts = net.getNodesById<HostNode>().values

        // Increase the number of actions to be taken to complete the current context
        // progress bar by the number of flows that will need to be started.
        this@ProgressBar.increaseMax(hosts.size.toLong())

        val random = this@NetSimScope.config.random

        val randomPermutationMap = buildMap {
            val destSet = hosts.toSet()

            hosts.forEach { sender ->
                var dest: HostNode

                if (destSet.size == 1 && destSet.first() == sender)
                    error("Change algorithm if this happens")

                do {
                    dest = destSet.random(random)
                } while (dest === sender)

                put(sender, dest)
            }
        }

        randomPermutationMap.forEach { (sender, dest) ->
            net.startFlow(
                devConfig.netFlowConfig.version(
                    senderId = sender.id,
                    destId = dest.id,
                    demand = demandMapping(sender),
                )
            )

            this@ProgressBar.step()
        }
    }
}
