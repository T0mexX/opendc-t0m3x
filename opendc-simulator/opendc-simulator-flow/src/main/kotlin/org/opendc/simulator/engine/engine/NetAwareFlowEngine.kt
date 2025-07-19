package org.opendc.simulator.engine.engine

import kotlinx.coroutines.runBlocking
import org.opendc.common.Dispatcher
import org.opendc.common.SusRunnable
import org.opendc.common.logger.logger
import org.opendc.simulator.engine.graph.SusUpdatableFlowNode
import org.opendc.simulator.engine.graph.updateSusIfAble
import org.opendc.simulator.network.api.integration.NetController
import org.opendc.simulator.network.utils.SuspendingODCNApi

/**
 * Customization of [FlowEngine] which allows for every invocation of [run] to make a unique bridge
 */
public class NetAwareFlowEngine(
    dispatcher: Dispatcher,
    private val netController: NetController,
): FlowEngine(dispatcher), SusRunnable {
    @OptIn(SuspendingODCNApi::class)
    private suspend fun doRunEngine(now: Long) {
        val cycleQueue = this.cycleQueue
        val eventQueue = this.eventQueue

        try {
            // Mark the engine as active to prevent concurrent calls to this method
            active = true

            // Sync network with simulation virtual time.
            netController.sync()

            // Execute all scheduled updates at current timestamp
            while (true) {
                val ctx = eventQueue.poll(now) ?: break

                 ctx.updateSusIfAble(now)
            }

            // Execute all immediate updates
            while (true) {
                var ctx = cycleQueue.poll()
                if (ctx == null) {
                    netController.sync() // Wait until the network is stable.

                    // Execute observers' handlers sequentially, since node invalidation must be sequential.
                    val callbacksExecuted: Int = netController.jNetController.execCallbacks()
                    // If at least one callback has been executed, there may be an invalidated node.
                    if (callbacksExecuted > 0) continue
                    else break
                }

                ctx.updateSusIfAble(now)
            }
        } finally {
            active = false
        }

        // Schedule an engine invocation for the next update to occur.
        val headDeadline = eventQueue.peekDeadline()
        if (headDeadline != Long.MAX_VALUE && headDeadline >= now) {
            trySchedule(futureInvocations, now, headDeadline)
        }
    }

    /**
     * Should be used if the `SimulationDispatcher` is not [SusRunnable] aware,
     * since it create a bridge to suspending execution every time.
     */
    @Deprecated(
        message = "bad performance. Use `susRun()` from an already suspending context if possible",
        replaceWith = ReplaceWith("susRun()")
    )
    override fun run(): Unit = runBlocking {
        if (warnLogged.not()) {
            warnLogged = true
            log.warn { "Running engine from non-suspending context. This results in reduced performance" }
        }
        doRunEngine(futureInvocations.poll())
    }

    /**
     * Should be invoked if the `SimulationDispatcher is [SusRunnable] aware.
     */
    override suspend fun susRun(): Unit = doRunEngine(futureInvocations.poll())

    private companion object {
        val log by logger()
        var warnLogged = false
    }
}
