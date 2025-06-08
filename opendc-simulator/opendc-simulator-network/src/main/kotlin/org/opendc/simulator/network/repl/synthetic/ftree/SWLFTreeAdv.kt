package org.opendc.simulator.network.repl.synthetic.ftree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.ftree.FTree
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.repl.synthetic.SyntheticWl
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.increaseMax

/**
 * Adversarial synthetic traffic pattern for fat-tree topology.
 *
 * In this pattern, each host sends traffic to a randomly selected (according to simulation seed)
 * host in a different pod, ensuring that all traffic is forced through the core layer of the fat-tree.
 */
@Serializable
@SerialName("ftree-adversarial")
internal object SWLFTreeAdv: SyntheticWl<FTree> {
    context(NetSimScope, ProgressBar)
    override suspend fun startSyntheticFlows(
        net: Network,
        demandMapping: (HostNode) -> DataRate
    ) {
        require(net is FTree)

        // Increase the number of actions to be taken to complete the current context
        // progress bar by the number of flows that will need to be started.
        this@ProgressBar.increaseMax(net.specs.N_.toLong())

        // The randomization seed of the simulation scope.
        val rndm = config.random

        net.pods.forEach { pod ->
            pod.hosts.forEach { h ->
                net.startFlow(
                    devConfig.netFlowConfig.version(
                        senderId = h.id,
                        // Random host on different pod, so that traffic goes through the core layer.
                        destId = net.pods.filterNot { it === pod }.random(rndm).hosts.random(rndm).id,
                        demand = demandMapping(h),
                    )
                )

                this@ProgressBar.step()
            }
        }
    }
}
