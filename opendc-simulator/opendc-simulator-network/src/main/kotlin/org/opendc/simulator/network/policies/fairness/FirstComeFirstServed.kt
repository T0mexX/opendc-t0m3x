package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.port.PortFlowEntry

/**
 * TODO
 */
@Serializable
@SerialName("fcfs")
internal class FirstComeFirstServed : FairnessPolicy() {
    context(Link)
    override suspend fun applyFairness(entryList: List<PortFlowEntry>) {
        TODO()
//        val p = this@Port
//        val l = p.txLink!!
//
//        processDemandReductions(entryList)
//
//        coroutineScope {
//            entryList.asFlow().onEach {
//                if (it.used.not()) return@onEach
//                val increaseBy = it.demand - it.tput
//                assert(increaseBy >= DataRate.zero) { increaseBy.value }
//                if (increaseBy approx  DataRate.zero) return@onEach
//                val claimedBw = l.claimBw(increaseBy, it.netF)
//                if (claimedBw approx  DataRate.zero) return@onEach
//                it.tput = (it.tput + claimedBw).roundToIfWithinEpsilon(it.demand, epsilon = 1.0)
//                assert(it.tput <= it.demand)
//            }.launchIn(this@coroutineScope)
    }
}
