package org.opendc.simulator.network.simscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.common.logger.logger
import org.opendc.simulator.network.components.node.NodeVersion
import org.opendc.simulator.network.components.port.PortVersion
import org.opendc.simulator.network.flow.internals.NetFlowVersion
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.utils.flyweight.internals.FWDispenser
import org.opendc.simulator.network.utils.flyweight.publics.FWId
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

    val portVersion: PortVersion
    val nodeVersion: NodeVersion
    val netFlowVersion: NetFlowVersion

    init {
        var ctx = coroutineContext
        DebugProbes.install()
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

        portVersion = devConfig.portConfig.version
        nodeVersion = devConfig.nodeConfig.version
        netFlowVersion = devConfig.netFlowConfig.version
        runBlocking { initDispensers() }
    }

    private suspend fun initDispensers() {
        portVersion.initDispensers()
        nodeVersion.initDispensers()
        netFlowVersion.initDispensers()
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
