package org.opendc.simulator.network.simscope.rateupdtpool

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.sync.prioritizedgroupmtx.FlyWeightPool
import org.opendc.simulator.network.sync.prioritizedgroupmtx.PoolIdx
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class FlowRateChangePool(
    override val nSubPools: Int = 10
): AbstractCoroutineContextElement(Key), FlyWeightPool<FlowRateChange> {
    private val subPools = 0.rangeTo(nSubPools).map { Channel<FlowRateChange>() }
    private var nextIdx: Int = 0
    private val nextIdxMtx = Mutex()

    override suspend fun acquire(poolIdx: PoolIdx): FlowRateChange {
        return subPools[poolIdx.i]
            .tryReceive()
            .getOrNull()
            ?: FlowRateChange(flyWeightPool = this, poolIdx = poolIdx)
    }

    override suspend fun getIdx(): PoolIdx = nextIdxMtx.withLock {
        PoolIdx(nextIdx % nSubPools).also { nextIdx++ }
    }

    override suspend fun dispose(obj: FlowRateChange) {
        subPools[obj.poolIdx.i].send(obj)
    }

    companion object Key : CoroutineContext.Key<FlowRateChangePool> {

        context(NetSimScope)
        operator fun invoke(): FlowRateChangePool =
            FlowRateChangePool(
                nSubPools = coroutineContext[NetSimConfig]!!.netSimDeveloperConfig.nSubPools
            )
    }
}
