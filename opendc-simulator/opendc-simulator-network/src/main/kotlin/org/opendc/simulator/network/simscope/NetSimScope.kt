package org.opendc.simulator.network.simscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.common.logger.logger
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.node.NodeVersion
import org.opendc.simulator.network.components.port.PortVersion
import org.opendc.simulator.network.flow.internals.NetFlowVersion
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class NetSimScope(
    override var coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : CoroutineScope {

    val ctx: CoroutineContext get() = coroutineContext

    val config: NetSimConfig
    val barrier: NetSimBarrier
    val devConfig: NetSimDevConfig
    val poolAggr: NetSimPoolAggregator
    val idDispenser: NetSimIdDispenser
    val enRecorder: NetSimEnRecorder
    val tmSrc: NetSimTmSrc<*>
    val routPolicy: RoutPolicy
    val log by logger()
    val net: Network get() = _net
    private lateinit var _net: Network

    val portVersion: PortVersion
    val nodeVersion: NodeVersion
    val netFlowVersion: NetFlowVersion

    init {
        var tmpCtx = coroutineContext
        runBlocking {
            tmpCtx[Job] ?: let { tmpCtx += Job() }
            tmpCtx[NetSimConfig] ?: let { tmpCtx += NetSimConfig() }
            tmpCtx[NetSimBarrier] ?: let {
                tmpCtx +=  NetSimBarrier(tmpCtx[NetSimConfig]!!)
            }
            tmpCtx[NetSimPoolAggregator] ?: let {
                tmpCtx += NetSimPoolAggregator(
                    tmpCtx[NetSimConfig]!!
                        .netSimDevConfig
                        .flyWeightConfig
                        .nSubPools
                )
            }
            tmpCtx[NetSimIdDispenser] ?: let { tmpCtx += NetSimIdDispenser() }
            tmpCtx[NetSimTmSrc] ?: let { tmpCtx += NetSimTmSrc.Internal() }
            tmpCtx[NetSimEnRecorder] ?: let { tmpCtx += NetSimEnRecorder(tmpCtx[NetSimTmSrc]!!) }
            tmpCtx[RoutPolicy] ?: let { tmpCtx += tmpCtx[NetSimConfig]!!.routPolicy }
        }
        coroutineContext = tmpCtx
        config = tmpCtx[NetSimConfig]!!
        barrier = tmpCtx[NetSimBarrier]!!
        devConfig = config.netSimDevConfig
        poolAggr = tmpCtx[NetSimPoolAggregator]!!
        idDispenser = tmpCtx[NetSimIdDispenser]!!
        tmSrc = tmpCtx[NetSimTmSrc]!!
        enRecorder = tmpCtx[NetSimEnRecorder]!!
        routPolicy = tmpCtx[RoutPolicy]!!

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
    internal fun registerNetwork(net: Network) {
        require(::_net.isInitialized.not()) {
            "A network was already registered for this scope"
        }
        _net = net
    }
//
//    internal fun registerAutoExporter(exporter: NetSimAutoExporter) {
//        require(autoExporter == null)
//
//        ctx += exporter
//        coroutineContext += exporter
//    }

    /**
     * TODO
     */
    internal suspend fun sync(forceUpdt: Boolean = false) {
        barrier.awaitStability()
        barrier.whileStable(NetSimStabilityMode.CHECKED) {
            enRecorder.sync(forceUpdt)
        }
        // TODO: net.sync
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
    }
}
