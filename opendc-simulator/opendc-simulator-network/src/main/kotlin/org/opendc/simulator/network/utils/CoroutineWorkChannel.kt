package org.opendc.simulator.network.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class CoroutineWorkChannel<T> private constructor(
    nCoroutines: Int = Runtime.getRuntime().availableProcessors(),
    scope: CoroutineScope,
    private val chl: Channel<T>,
    block: suspend (T) -> Unit,
) : Channel<T> by chl, AutoCloseable {

    constructor(
        nCoroutines: Int = Runtime.getRuntime().availableProcessors(),
        scope: CoroutineScope,
        block: suspend (T) -> Unit,
    ) : this(nCoroutines, scope, Channel<T>(Channel.UNLIMITED), block)

    private val coroutines: List<Job>

    init {
        coroutines = buildList {
            repeat(nCoroutines) {
                add(
                    scope.launch {
                        while (true) block(chl.receive())
                    }
                )
            }
        }
    }

    override fun close() {
        coroutines.forEach { it.cancel() }
    }
}
