package org.opendc.simulator.network.api.integration

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.utils.LazyChangeHndlr
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


public abstract class SeqComputeIntegration: AbstractCoroutineContextElement(Key) {

    private val sequentialHndlrs = Channel<LazyChangeHndlr<*, *>>()

    public fun executeSequentialHndlrs(): Int = runBlocking {
        var count = 0
        while (true) {
            sequentialHndlrs.tryReceive().getOrNull()?.invoke() ?: break
            count++
        }
        count
    }

    internal suspend fun queueSeqHndlr(hndlr: LazyChangeHndlr<*, *>) {
        sequentialHndlrs.send(hndlr)
    }

    internal companion object Key : CoroutineContext.Key<SeqComputeIntegration> {
        fun CoroutineContext.getSeqChl(): Channel<LazyChangeHndlr<*, *>> =
            this[SeqComputeIntegration]
                ?.sequentialHndlrs
                ?: throw IllegalStateException("coroutine context $this does not provide a `SeqComputeIntegration`, but one is needed")

    }

    public enum class Mode {
        SEQUENTIAL,
        SUSPENDING,
        BOTH
    }
}
