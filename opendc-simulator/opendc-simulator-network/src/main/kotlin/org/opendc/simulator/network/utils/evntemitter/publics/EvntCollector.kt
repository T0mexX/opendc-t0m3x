package org.opendc.simulator.network.utils.evntemitter.publics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorChl

/**
 * Provides a [Channel]-based interface for collecting events from an [EvntEmitter]'s [evntFlow],
 * allowing the use of [select] with event-driven code.
 *
 * Since `SharedFlow` does not support selection via `select` clauses directly,
 * [EvntCollector] bridges the gap by exposing a [Channel] that emits values
 * from the underlying [SharedFlow]. This enables coroutines to wait for multiple
 * events from different sources concurrently using `select`.
 *
 * @property evntFlow The event-producing [SharedFlow] from the associated [EvntEmitter].
 */
public class EvntCollector<T: EvntEmitter<T>> private constructor(
    private val evntFlow: EvntFlow<T>,
) : Channel<Evnt<*, T>> by Channel(), Launchable, AutoCloseable {
    /**
     * Coroutine job that collects from `evntFlow` and sends the events to the delegated channel.
     */
    private lateinit var job: Job

    /**
     * TODO
     */
    context(NetSimScope)
    override suspend fun netLaunch(scope: CoroutineScope): Job = scope.launch(CoroutineID.new()) {
        evntFlow.collect { evnt ->
            // Retrieve from `EvntFlow` and send it to the `EvntCollector` channel.
            send(evnt)
            // Now evnt can be retrieved using `select` by whoever is using this collector.
        }
    }.also { job = it }

    /**
     * - Cancel the coroutine that collects from the [EvntFlow].
     * - Closes the [EvntCollector]~[Channel].
     */
    override fun close() {
        job.cancel()
        (this as Channel<*>).close()
    }

    internal companion object {
        /**
         * @param scope If defined, the collector will be launched in this scope,
         * else it will be launched in [NetSimScope] context parameter.
         */
        context(NetSimScope)
        suspend operator fun <T: EvntEmitter<T>> invoke(
            scope: CoroutineScope = this@NetSimScope,
            evntFlow: EvntFlow<T>
        ) =
            // TODO: maybe add config for buffer sizes etc.
            EvntCollector(evntFlow = evntFlow).also {
                with(it) { netLaunch(scope) }
            }
    }
}
