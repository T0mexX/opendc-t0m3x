package org.opendc.simulator.network.repl.synthetic

import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal interface SyntheticWl<in T: Network> {
    /**
     * TODO
     * @param demandMapping Maps each [HostNode] to their new flow demand.
     */
    context(NetSimScope, ProgressBar)
    suspend fun startSyntheticFlows(
        net: Network,
        demandMapping: (HostNode) -> DataRate
    )
}
