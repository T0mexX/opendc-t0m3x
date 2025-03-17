package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.neww.publics.FlowId2
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId

internal data class PortFlowEntry(
//    override val pool: FWPool<PortFlowEntry, Companion>,
//    override val poolIdx: Idx,
    var used: Boolean = false,
    var flowId: FlowId2 = FlowId2.INVALID,
    var txDemand: DataRate = DataRate.zero,
    var txThroughput: DataRate = DataRate.zero,
)
//    : IFW<PortFlowEntry> {
//    companion object : FlyWeightId<PortFlowEntry> {
//        context(NetSimScope)
//        suspend fun dispenser(): FWDispenser<PortFlowEntry> =
//            poolAggr.getCreatePool(PortFlowEntry) { pool, idx ->
//                PortFlowEntry(
//                    pool = pool,
//                    poolIdx = idx
//                )
//            }.dispenser()
//    }
//}
