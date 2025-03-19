package org.opendc.simulator.network.simscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.common.logger.logger
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class NetSimScope(
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : CoroutineScope {

    val ctx: CoroutineContext = coroutineContext
    val config: NetSimConfig
    val barrier: NetSimBarrier
    val devConfig: NetSimDevConfig
    val poolAggr: NetSimPoolAggregator
    val idDispenser: NetSimIdDispenser
    val logger by logger()

    init {
        var ctx = coroutineContext
        runBlocking {
            ctx[Job] ?: let { ctx += Job() }
            ctx[NetSimConfig] ?: let { ctx += NetSimConfig.DEFAULT }
            ctx[NetSimBarrier] ?: let {
                ctx +=  NetSimBarrier(ctx[NetSimConfig]!!)
            }
            ctx[NetSimPoolAggregator] ?: let {
                ctx += NetSimPoolAggregator(
                    ctx[NetSimConfig]!!
                        .netSimDevConfig
                        .flyWeightConfig
                        .nSubPools
                )
            }
            ctx[NetSimIdDispenser] ?: let { ctx += NetSimIdDispenser() }
        }
        config = ctx[NetSimConfig]!!
        barrier = ctx[NetSimBarrier]!!
        devConfig = config.netSimDevConfig
        poolAggr = ctx[NetSimPoolAggregator]!!
        idDispenser = ctx[NetSimIdDispenser]!!
    }


//    launch

    companion object {
        internal fun <T> CoroutineScope.launchNetworkSim(
            ctx: CoroutineContext = EmptyCoroutineContext,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            block: suspend NetSimScope.() -> Unit,
        ): Job {

            return NetSimScope().launch {
                with(this as NetSimScope) {

                }
            }
//            NetSimScope(ctx).launch(block = block)
//            val newContext = newCoroutineContext(ctx)

//            val coroutine = object : Abstrac    StandaloneCoroutine(newContext, active = true)
//            coroutine.start(start, coroutine, block)
//            return coroutine
        }

        internal fun NetSimScope.scopeLaunch(
            ctx: CoroutineContext = EmptyCoroutineContext,
            block: suspend NetSimScope.() -> Unit
        ): Job = launch(ctx) {
            block()
        }
    }
}
