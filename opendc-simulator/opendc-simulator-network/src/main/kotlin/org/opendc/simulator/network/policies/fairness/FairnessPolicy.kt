package org.opendc.simulator.network.policies.fairness

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry

@Serializable
internal sealed interface FairnessPolicy {
    context(Port)
    suspend fun applyPolicy(entryList: List<PortFlowEntry>)

    context(Port)
    suspend fun processDemandReductions(entryList: List<PortFlowEntry>) = coroutineScope {
        entryList.asFlow().onEach { entry ->
            if (entry.used.not()) return@onEach
            if (entry.demand < entry.tput) {
                this@Port.txLink!!.releaseBw(entry.tput - entry.demand, entry.netF)
                entry.tput = entry.demand
            }
        }.launchIn(this@coroutineScope)
    }
}
