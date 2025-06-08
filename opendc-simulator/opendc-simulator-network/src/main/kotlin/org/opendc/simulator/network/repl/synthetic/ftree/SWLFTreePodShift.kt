package org.opendc.simulator.network.repl.synthetic.ftree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.ftree.FTree
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.repl.synthetic.SyntheticWl
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.increaseMax

@Serializable
@SerialName("ftree-podshift")
internal object SWLFTreePodShift: SyntheticWl<FTree> {
    context(NetSimScope, ProgressBar) override suspend fun startSyntheticFlows(
        net: Network,
        demandMapping: (HostNode) -> DataRate
    ) {
        require(net is FTree)

        // Increase the number of actions to be taken to complete the current context
        // progress bar by the number of flows that will need to be started.
        this@ProgressBar.increaseMax(net.specs.N_.toLong())


        net.pods.forEachIndexed { pIdx, pod ->
            pod.hosts.forEachIndexed { hIdx, h ->
                // Destination pod.
                val destP = net.pods.getModuloIdx(pIdx + 1)
                // Destination host.
                val destH = destP.hosts[hIdx]

                net.startFlow(
                    devConfig.netFlowConfig.version(
                        senderId = h.id,
                        destId = destH.id,
                        demand = demandMapping(h),
                    )
                )

                this@ProgressBar.step()
            }
        }
    }


    private fun <T> List<T>.getModuloIdx(idx: Int): T = this[idx % this.size]
}
