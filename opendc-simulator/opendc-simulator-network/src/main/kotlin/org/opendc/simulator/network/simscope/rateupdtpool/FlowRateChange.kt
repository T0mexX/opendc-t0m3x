package org.opendc.simulator.network.simscope.rateupdtpool

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.sync.prioritizedgroupmtx.FlyWeightPool
import org.opendc.simulator.network.sync.prioritizedgroupmtx.PoolIdx
import org.opendc.simulator.network.sync.prioritizedgroupmtx.FlyWeight

internal data class FlowRateChange(
    override val flyWeightPool: FlyWeightPool<FlowRateChange>,
    override val poolIdx: PoolIdx,
    var from: DataRate = DataRate.zero,
    var to: DataRate = DataRate.zero,
) : FlyWeight<FlowRateChange>
