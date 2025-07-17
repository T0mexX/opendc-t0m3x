package org.opendc.simulator.engine.engine

import org.opendc.common.Dispatcher
import org.opendc.simulator.network.api.integration.JNetController
import org.opendc.simulator.network.api.integration.NetController
import java.time.InstantSource
import kotlin.coroutines.CoroutineContext

public class SusFlowEngine(
    dispatcher: Dispatcher,
    netController: NetController?,
): FlowEngine(dispatcher, netController) {
    /**
     * Create a new [FlowEngine] instance using the specified [CoroutineContext] and [InstantSource].
     */
    public fun create(dispatcher: Dispatcher, netController: NetController?): FlowEngine {
        return FlowEngine(dispatcher, netController)
    }

    fun create(dispatcher: Dispatcher): FlowEngine {
        return FlowEngine(dispatcher, null)
    }

    fun FlowEngine(dispatcher: Dispatcher, netController: JNetController?) {
        this.dispatcher = dispatcher
        this.clock = dispatcher.timeSource
        this.netController = netController
    }

    public companion object {
    }
}
