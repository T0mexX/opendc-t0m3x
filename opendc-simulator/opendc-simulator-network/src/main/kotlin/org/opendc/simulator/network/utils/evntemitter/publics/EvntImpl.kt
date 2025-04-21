package org.opendc.simulator.network.utils.evntemitter.publics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable

internal abstract class EvntImpl<Self, T> : Evnt<Self, T>, IFW<Self>
    where Self: Evnt<Self, T>, T: EvntEmitter<T> {

    /**
     * Number of coroutines that are going to collect this event.
     * Used to determine how many times `dispose` needs to be invoked.
     */
    private var nListeners: Int = 0
    private val nListenersMtx = Mutex()

    override suspend fun dispose() = nListenersMtx.withLock {
        assert(nListeners > 0)
        nListeners--

        // If this event is network-stability-invalidatable then mark it as valid.
        (this as? Invalidatable)?.validate()

        // If no other listener still
        // needs to handle the event, then dispose.
        if (nListeners == 0) super.dispose()
    }

    /**
     * - Sets the number of listeners that are going to collect this event.
     * This method is invoked when emitting an [Evnt] in a [EvntFlow].
     * - If this [Evnt] is [Invalidatable] then it is mark as unstable.
     */
    context(EvntFlow<T>)
    internal suspend fun setUp(nListeners: Int) = nListenersMtx.withLock {
        assert(nListeners >= 0)

        // If this event is network-stability-invalidatable then it is mark as unstable.
        (this as? Invalidatable)?.invalidate()

        this.nListeners = nListeners
    }
}
