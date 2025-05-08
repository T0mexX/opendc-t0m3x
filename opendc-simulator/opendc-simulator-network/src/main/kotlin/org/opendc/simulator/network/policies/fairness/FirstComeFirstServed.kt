package org.opendc.simulator.network.policies.fairness

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry

/**
 * TODO
 */
@Serializable
@SerialName("fcfs")
internal class FirstComeFirstServed : FairnessPolicy() {
    context(Port)
    override suspend fun applyFairness(entryList: List<PortFlowEntry>) {
        val p = this@Port
        val l = p.txLink!!

        processDemandReductions(entryList)

        coroutineScope {
            entryList.asFlow().onEach {
                if (it.used.not()) return@onEach
                val increaseBy = it.demand - it.tput
                assert(increaseBy >= DataRate.zero) { increaseBy.value }
                if (increaseBy approx  DataRate.zero) return@onEach
                val claimedBw = l.claimBw(increaseBy, it.netF)
                if (claimedBw approx  DataRate.zero) return@onEach
                it.tput = (it.tput + claimedBw).roundToIfWithinEpsilon(it.demand, epsilon = 1.0)
                assert(it.tput <= it.demand)
            }.launchIn(this@coroutineScope)
        }
    }
}
