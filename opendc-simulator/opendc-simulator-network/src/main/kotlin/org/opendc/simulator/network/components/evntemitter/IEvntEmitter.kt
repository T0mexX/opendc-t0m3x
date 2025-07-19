/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:OptIn(InternalODCNApi::class)

package org.opendc.simulator.network.components.evntemitter

import kotlinx.coroutines.channels.ClosedSendChannelException
import org.opendc.simulator.network.components.invalidatable.Invalidatable
import org.opendc.simulator.network.utils.InternalODCNApi

/**
 * TODO
 */
internal open class IEvntEmitter<Self : EvntEmitter<Self>> : EvntEmitter<Self> {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Private Implementation
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The list containing all the [EvntListener]s listening to this [EvntEmitter].
     */
    private val listeners = mutableListOf<EvntListener<Self>>()

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Retrieves a new [EvntListener] to listen to events emitter by this [EvntEmitter].
     */
    override fun evntListener(): EvntListener<Self> = EvntListener<Self>().also { listeners.add(it) }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Internal (public with Opt-in)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The number of [EvntListener]s that are listening to this [EvntEmitter].
     */
    final override val nListeners: Int get() = listeners.size

    /**
     * Emits [evnt] to all [EvntListener]s that are listening to this [EvntEmitter].
     * @param evnt The event to be emitted.
     */
    override suspend fun emit(evnt: Evnt<Self, *>)  {
        // `nListeners` collectors will need to call `handled` on this event.
        evnt.nCollectors = nListeners
        // If the fact that the event has not been handled yet invalidates
        // network stability (hence evt is `invalidatable`),
        // then invalidate.
        // The event will be validated again once all collectors handled the event.
        if (listeners.isNotEmpty()) (evnt as? Invalidatable)?.invalidate()
        // Send the event to all collectors.
        listeners.forEach { c ->
            try {
                c.sendChl.send(evnt)
            } catch (e: ClosedSendChannelException) {
                evnt.markHandled()
            }
        }
    }
}
