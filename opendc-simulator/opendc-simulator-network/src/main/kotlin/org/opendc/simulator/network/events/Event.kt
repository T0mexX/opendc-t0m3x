package org.opendc.simulator.network.events

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal abstract class Event<T: WithEvents<T>, O> {
    private val completionMtx = Mutex()
    private var result: O? = null

    internal suspend fun await(): O = completionMtx.withLock { result!! }

    abstract fun trigger(): O
}
