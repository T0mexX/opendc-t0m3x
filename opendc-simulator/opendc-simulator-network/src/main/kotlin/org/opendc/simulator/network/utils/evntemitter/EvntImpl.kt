package org.opendc.simulator.network.utils.evntemitter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import kotlin.coroutines.coroutineContext

internal abstract class EvntImpl<T: EvntEmitter<T>, Self: EvntImpl<T, Self>>(
    override val pool: FWPool<Self, FWId<Self>>,
    override val poolIdx: Int,
) : IEvnt<T, Self> {
    /**
     * TODO
     */
    override var nCollectors: Int = 0

    /**
     * TODO
     */
    private val nHandledMtx = Mutex()

    /**
     * TODO
     */
    private var nHandled: Int = 0

    /**
     * TODO
     */
    protected val state = MutableStateFlow(IEvnt.State.UNTRACKED)

    /**
     * TODO
     */
    protected var emitter: CoroutineID? = null


    override suspend fun awaitHandling(): Self {
        state.first {
            // If `state` is `null`, msg was sent with `dispose = true` which means
            // the message flyweight object might have been reused by now.
            assert(emitter == coroutineContext[CoroutineID]) { "await on recycled msg" }
            it == IEvnt.State.HANDLED
        }
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    override suspend fun reset(builderBlock: (suspend Self.() -> Unit)?): Self {
        @Suppress("UNCHECKED_CAST")
        this as Self

        state.emit(IEvnt.State.UNTRACKED)
        emitter = null

        builderBlock?.invoke(this)

        return this
    }

    override suspend fun emit(from: T, dispose: Boolean): Self {
        // If dispose is false, `emitter` wants to wait for the evnt to be handled;
        // hence `state` is going to be tracked, and this `msg` is not going to be disposed by the receiver.
        if (dispose.not()) {
            emitter = coroutineContext[CoroutineID]!!
            state.emit(IEvnt.State.PENDING)
        }

        @Suppress("UNCHECKED_CAST")
        (from as IEvntEmitter<T>).emit(this)

        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    override suspend fun handled() {
        nHandledMtx.withLock {
            if (++nHandled == nCollectors) {

                // If the fact that this event is not yet handled by all collectors invalidates the network
                // (hence the evnt is invalidatable), then validate it once it is handled.
                (this as? Invalidatable)?.validate()

                // If the emitter tracks evnt state, then communicate that msg
                // was handled (evnt is going to be disposed by the emitter)
                if (state.value == IEvnt.State.PENDING) {
                    state.emit(IEvnt.State.HANDLED)


                // Else, after evnt is handled, it can safely be disposed of.
                } else dispose()
            }
        }
    }
}
