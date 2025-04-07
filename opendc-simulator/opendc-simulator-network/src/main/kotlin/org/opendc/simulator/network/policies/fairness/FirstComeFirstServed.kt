package org.opendc.simulator.network.policies.fairness

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry

/**
 * TODO
 */
internal data object FirstComeFirstServed : FairnessPolicy {
    context(Port)
    override suspend fun applyPolicy(entryList: List<PortFlowEntry>, reductionsToBeExecuted: Boolean) {
        val p = this@Port
        val l = p.txLink!!
        if (reductionsToBeExecuted) processDemandReductions(entryList)

        coroutineScope {
            entryList.asFlow().onEach {
                if (it.used.not()) return@onEach
                val increaseBy = (it.demand - it.tput)
                check(increaseBy >= DataRate.zero)
                if(increaseBy approx DataRate.zero) return@onEach
                val claimedBw = l.claimBw(increaseBy)
                if (claimedBw approx DataRate.zero) return@onEach
                it.tput += claimedBw
                l.msgAsyncRxUpdt(deltaRate = claimedBw, it.netF)
            }.launchIn(this)
        }
    }
}
