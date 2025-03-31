package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry

internal object MaxMin : FairnessPolicy {
    context(Port) override suspend fun applyPolicy(entryList: List<PortFlowEntry>, reductionsToBeExecuted: Boolean) {
        TODO("Not yet implemented")
    }
}
