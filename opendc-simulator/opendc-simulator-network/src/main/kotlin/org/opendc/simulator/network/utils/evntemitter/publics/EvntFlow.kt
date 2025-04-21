package org.opendc.simulator.network.utils.evntemitter.publics

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable

/**
 * TODO
 */
internal class EvntFlow<T: EvntEmitter<T>> private constructor(
    private val delegatedF: MutableSharedFlow<Evnt<*, T>>,
) : MutableSharedFlow<Evnt<*, T>> by delegatedF {

    constructor() : this(
        delegatedF = MutableSharedFlow(
            // Events are not retroactively received. Only events after
            // a coroutine registers collecting the flow will be received.
            replay = 5,
            // Emitter coroutine will not suspend until the buffer is not full.
            // The `EvntEmitter` will be able to emit 1 evnt without suspending.
            // Before emitting the next one, all collectors will need to have collected the event.
            extraBufferCapacity = 0,
            onBufferOverflow = BufferOverflow.SUSPEND
        )
    )

    /**
     * We need to keep track of listener number because if `select`
     * is used to "collect", the number of subscribed coroutines
     * is not tracked (`onSubscription` is not invoked).
     *
     * This is used to determine how many `dispose` need to be
     * invoked on the event (by event listeners) before the event
     * object is sent back to the flyweight pool.
     */
    private var nListeners: Int = 0
    private val nListenersMtx = Mutex()

    /**
     * Sets up the number of listeners on the [Evnt] before emitting,
     * so that the flyweight system can work properly
     */
    override suspend fun emit(value: Evnt<*, T>) {
        value as EvntImpl<*, T>

        (value as? Invalidatable)?.invalidate()

        // Maintaining the lock while emitting is essential to avoid the case:
        // `setNListeners()` -> `registerListener()` -> `emit()`
        nListenersMtx.withLock {
            value.setUp(nListeners)
            delegatedF.emit(value)
        }
    }
}
