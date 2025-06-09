//package org.opendc.simulator.network.policies.fairness
//
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.flow.asFlow
//import kotlinx.coroutines.flow.launchIn
//import kotlinx.coroutines.flow.onEach
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//import org.opendc.common.units.DataRate
//import org.opendc.common.units.Unit.Companion.sumOfUnit
//import org.opendc.simulator.network.components.port.Port
//import org.opendc.simulator.network.components.port.PortFlowEntry
//
///**
// * TODO
// *
// */
////@Serializable
////@SerialName("proportional")
////internal class Proportional: FairnessPolicy() {
////    context(Port)
////    override suspend fun applyFairness(entryList: List<PortFlowEntry>) {
////        val p = this@Port
////        val l = p.txLink!!
////        // Sum of all the demands at this port.
////        val dmndSum = entryList.sumOfUnit { it.demand }
////
////        coroutineScope {
////
////            // Map each port flow entry with the delta data-rate that should be applied.
////            entryList.asFlow().let { f ->
////                // Apply data-rate reductions.
////                f.onEach { e ->
////                    if (e.used.not()) return@onEach
////                    val delta = (  (l.maxBw * (e.demand / dmndSum)  ) min e.demand) - e.tput
////                    if (delta >= DataRate.zero) return@onEach
////                    l.releaseBw(-delta, e.netF)
////                    e.tput = (e.tput + delta).roundToIfWithinEpsilon(e.demand, 1e-6)
////                }.launchIn(this@coroutineScope).join()
////
////                // Apply data-rate increases.
////                f.onEach { e ->
////                    if (e.used.not()) return@onEach
////                    val delta = (  (l.maxBw * (e.demand / dmndSum)  ) min e.demand) - e.tput
////                    if (delta <= DataRate.zero) return@onEach
////                    val claimed = l.claimBw(delta, e.netF)
////                    if (claimed approx DataRate.zero) return@onEach
////                    e.tput = (e.tput + delta).roundToIfWithinEpsilon(e.demand, 1e-6)
////                }.launchIn(this@coroutineScope).join()
////            }
////        }
////    }
////
////}
