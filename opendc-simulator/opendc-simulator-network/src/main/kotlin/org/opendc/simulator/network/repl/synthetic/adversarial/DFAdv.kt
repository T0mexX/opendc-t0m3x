package org.opendc.simulator.network.repl.synthetic.adversarial

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.DragonFly
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * Adversarial synthetic traffic pattern for dragon-fly topology.
 *
 * In this pattern, each host in group `i` sends traffic to a randomly selected
 * host in group `i+1` (according to simulation seed), stress testing intergroup channels.
 *
 * This worst-case (WC) adversarial traffic pattern is described
 * by John Kim et al. in "Technology-Driven, Highly-Scalable Dragonfly Topology"
 * Source: https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=4556717
 */
@Serializable
@SerialName("df-adversarial")
internal object DFAdv: AdversarialWl<DragonFly> {
    context(NetSimScope) override suspend fun startSyntheticFlows(net: Network, demandMapping: (HostNode) -> DataRate) {
        require(net is DragonFly)

        // The randomization seed of the simulation scope.
        val rndm = config.random

        net.groups.forEachIndexed { gIdx, g ->
            g.hosts.forEach { h ->
                // Destination group.
                val destG = net.groups.getModuloIdx(gIdx + 1)
                // Destination host.
                val destH = destG.hosts.random(rndm)

                // Start the flow in the network.
                net.startFlow(
                    devConfig.netFlowConfig.version(
                        senderId = h.id,
                        destId = destH.id,
                        demand = demandMapping(h),
                    )
                )
            }
        }
    }

    private fun <T> List<T>.getModuloIdx(idx: Int): T = this[idx % this.size]
}
