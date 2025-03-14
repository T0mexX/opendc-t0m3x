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

@file:OptIn(InternalUse::class)

package org.opendc.simulator.network.utils.observable

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.annotations.InternalUse
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Key.seqChangeHndlrChl
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Key.seqEventHndlrChl
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import org.opendc.simulator.network.utils.observable.elements.ObservableEvent
import org.opendc.simulator.network.utils.observable.elements.ObservableProperty
import org.opendc.simulator.network.utils.observable.handlers.EventHndlr
import org.opendc.simulator.network.utils.observable.handlers.SusEventHndlr
import org.opendc.simulator.network.utils.observable.handlers.lazy
import kotlin.coroutines.coroutineContext

@Suppress("UNCHECKED_CAST")
internal class DelegatedObservable<T : Observable<T>>(private val mode: Mode = Mode.BOTH) : Observable<T> {
    private lateinit var delegator: T
    private val changeHndlrLs = mutableMapOf<ObservableProperty<T, *>, ChangeHndlrLs<T, *>>()
    private val changeLsMtx = Mutex()
    private val eventHndlrLs = mutableMapOf<ObservableEvent<T>, EventHndlrLs<T>>()
    private val eventLsMtx = Mutex()


    override fun setUpDelegatedObservable(delegator: T) {
        this.delegator = delegator
    }

    override suspend fun <N> withChangeHndlr(
        prop: ObservableProperty<T, N>,
        hndlr: SusChangeHndlr<T, N>,
    ) = changeLsMtx.withLock {
        changeHndlrLs.getOrPut(prop) { ChangeHndlrLs<T, N>(mode) } as ChangeHndlrLs<T, N> addSus (hndlr)
        delegator
    }

    override fun <N> withChangeHndlrSeq(
        prop: ObservableProperty<T, N>,
        hndlr: ChangeHndlr<T, N>,
    ) = runBlocking {
        changeLsMtx.withLock {
            changeHndlrLs.getOrPut(prop) { ChangeHndlrLs<T, N>(mode) } as ChangeHndlrLs<T, N> addSeq (hndlr)
        }
        delegator
    }

    override suspend fun withEventHndlr(
        event: ObservableEvent<T>,
        hndlr: SusEventHndlr<T>,
    ) = eventLsMtx.withLock {
        eventHndlrLs.getOrPut(event) { EventHndlrLs(mode) } addSus (hndlr)
        delegator
    }

    override fun withEventHndlrSeq(
        event: ObservableEvent<T>,
        hndlr: EventHndlr<T>,
    ) = runBlocking {
        changeLsMtx.withLock {
            eventHndlrLs.getOrPut(event) { EventHndlrLs(mode) } addSeq (hndlr)
        }
        delegator
    }

    override suspend fun <N> triggerHandlers(
        prop: ObservableProperty<T, N>,
        old: N,
        new: N,
    ) {
        val ls = changeHndlrLs.getOrPut(prop) { ChangeHndlrLs<T, N>(mode) } as ChangeHndlrLs<T, N>
        ls.handleAll(delegator, old, new)
    }

    @InternalUse
    override suspend fun triggerHandlers(event: ObservableEvent<T>) {
        eventHndlrLs.getOrPut(event) { EventHndlrLs(mode) }.handleAll(delegator)
    }

    internal class EventHndlrLs<O: Observable<O>>(private val mode: Mode = Mode.BOTH) {
        private val seq by lazy { mutableListOf<EventHndlr<O>>() }
        private val sus by lazy { mutableListOf<SusEventHndlr<O>>() }

        internal infix fun addSeq(f: EventHndlr<O>) {
            if (mode == Mode.BOTH || mode == Mode.SEQUENTIAL) {
                seq.add(f)
            }
        }

        internal infix fun addSus(f: SusEventHndlr<O>) {
            if (mode == Mode.BOTH || mode == Mode.SUSPENDING) {
                sus.add(f)
            }
        }

        internal suspend fun handleAll(
            obj: O,
        ) {
            if ((mode == Mode.SEQUENTIAL || mode == Mode.BOTH) && seq.isNotEmpty()) {
                val chl = coroutineContext.seqEventHndlrChl()
                seq.forEach { chl.send(it.lazy(obj)) }
            }
            if (mode == Mode.SUSPENDING || mode == Mode.BOTH) {
                sus.forEach { it.handle(obj) }
            }
        }
    }

    internal class ChangeHndlrLs<O: Observable<O>, T>(private val mode: Mode = Mode.BOTH) {
        private val seq by lazy { mutableListOf<ChangeHndlr<O, T>>() }
        private val sus by lazy { mutableListOf<SusChangeHndlr<O, T>>() }

        internal infix fun addSeq(f: ChangeHndlr<O, T>) {
            if (mode == Mode.BOTH || mode == Mode.SEQUENTIAL) {
                seq.add(f)
            }
        }

        internal infix fun addSus(f: SusChangeHndlr<O, T>) {
            if (mode == Mode.BOTH || mode == Mode.SUSPENDING) {
                sus.add(f)
            }
        }

        internal suspend fun handleAll(
            obj: O,
            old: T,
            new: T,
        ) {
            if ((mode == Mode.SEQUENTIAL || mode == Mode.BOTH) && seq.isNotEmpty()) {
                val chl = coroutineContext.seqChangeHndlrChl()
                seq.forEach { chl.send(it.lazy(obj, old, new)) }
            }
            if (mode == Mode.SUSPENDING || mode == Mode.BOTH) {
                sus.forEach { it.handle(obj, old, new) }
            }
        }
    }
}
