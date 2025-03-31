package org.opendc.simulator.network.policies.fairness

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry

internal data object FirstComeFirstServed : FairnessPolicy {
    context(Port)
    override suspend fun applyPolicy(entryList: List<PortFlowEntry>, reductionsToBeExecuted: Boolean) {
        val p = this@Port
        val l = p.txLink!!
        if (reductionsToBeExecuted) processDemandReductions(entryList)

        entryList.forEach {
            val increaseBy = (it.demand - it.tput)
            check(increaseBy >= DataRate.zero)
            if (l.claimBw(increaseBy) approx DataRate.zero) return
            it.tput += increaseBy
        }
    }
}
