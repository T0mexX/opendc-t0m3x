package org.opendc.simulator.network.simscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.NetSimEnRecorder
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.node.NodeVersion
import org.opendc.simulator.network.components.port.PortVersion
import org.opendc.simulator.network.flow.internals.NetFlowVersion
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.utils.sync.Synchronizable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class NetSimScope(
    override var coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : CoroutineScope {

    val ctx: CoroutineContext = coroutineContext
    val config: NetSimConfig
    val barrier: NetSimBarrier
    val devConfig: NetSimDevConfig
    val poolAggr: NetSimPoolAggregator
    val idDispenser: NetSimIdDispenser
    val enRecorder: NetSimEnRecorder
    val tmSrc: NetSimTmSrc<*>
    val logger by logger()
    val net: Network get() = _net
    private lateinit var _net: Network

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
            ctx[NetSimTmSrc] ?: let { ctx += NetSimTmSrc.Internal() }
            ctx[NetSimEnRecorder] ?: let { ctx += NetSimEnRecorder(ctx[NetSimTmSrc]!!) }
        }
        coroutineContext = ctx
        config = ctx[NetSimConfig]!!
        barrier = ctx[NetSimBarrier]!!
        devConfig = config.netSimDevConfig
        poolAggr = ctx[NetSimPoolAggregator]!!
        idDispenser = ctx[NetSimIdDispenser]!!
        tmSrc = ctx[NetSimTmSrc]!!
        enRecorder = ctx[NetSimEnRecorder]!!

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

    /**
     * TODO
     */
    internal fun registerNetworkInScope(net: Network) {
        require(::_net.isInitialized.not()) {
            "A network was already registered for this scope"
        }
        _net = net
    }

    /**
     * TODO
     */
    internal suspend fun sync(forceUpdt: Boolean = false) {
        enRecorder.sync(forceUpdt)
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
            block: suspend NetSimScope.() -> Unit
        ): Job = launch(ctx) {
            block()
        }

        internal fun <T> NetSimScope.scopeAsync(
            block: suspend NetSimScope.() -> T
        ): Deferred<T> = async(ctx) {
            block()
        }
    }
}
