package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry

@Serializable
internal sealed interface FairnessPolicy {
    context(Port)
    suspend fun applyPolicy(entryList: List<PortFlowEntry>, reductionsToBeExecuted: Boolean)

    context(Port)
    suspend fun processDemandReductions(entryList: List<PortFlowEntry>) {
        entryList.forEach { entry ->
            if (entry.demand < entry.tput) {
                this@Port.txLink!!.releaseBw(entry.tput - entry.demand, entry.netF)
            }
        }
    }
}
