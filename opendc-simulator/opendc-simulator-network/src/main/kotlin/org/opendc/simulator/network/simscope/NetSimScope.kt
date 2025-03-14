package org.opendc.simulator.network.simscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.components.stability.NetworkStabilityBarrier
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class NetSimScope(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    netSimConfig: NetSimConfig,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        coroutineContext

    init {
        var ctx = coroutineContext
        runBlocking {
            ctx[Job] ?: let { ctx += Job() }
            ctx[NetSimConfig] ?: let { ctx += NetSimConfig.DEFAULT }
//            ctx[NetSimBarrier] ?: let {
//                ctx +=  NetSimBarrier()
//            }
        }
    }

}

