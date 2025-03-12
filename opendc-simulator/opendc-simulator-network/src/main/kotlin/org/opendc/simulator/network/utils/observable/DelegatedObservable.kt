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
import org.opendc.simulator.network.utils.ChangeHndlr
import org.opendc.simulator.network.utils.SusChangeHndlr

@Suppress("UNCHECKED_CAST")
internal class DelegatedObservable<T : Observable<T>> : Observable<T> {
    private val hndlrs = mutableMapOf<ObservableProperty<T, *>, HndlrsLs<T, *>>()
    private val hndlrsLsMtx = Mutex()
    private val hndlMtx = Mutex()

    override suspend fun <N> withHandler(
        prop: ObservableProperty<T, N>,
        hndlr: SusChangeHndlr<T, N>,
    ) = hndlrsLsMtx.withLock {
        hndlrs.getOrPut(prop) { HndlrsLs<T, N>() } as HndlrsLs<T, N> addSus (hndlr)
    }

    override fun <N> withHandlerSeq(
        prop: ObservableProperty<T, N>,
        hndlr: ChangeHndlr<T, N>,
    ) = runBlocking {
        hndlrsLsMtx.withLock {
            hndlrs.getOrPut(prop) { HndlrsLs<T, N>() } as HndlrsLs<T, N> addSeq (hndlr)
        }
    }

    override suspend fun <N> handleChange(
        prop: ObservableProperty<T, N>,
        old: N,
        new: N,
    ) = hndlMtx.withLock {
        val ls = hndlrs.getOrPut(prop) { HndlrsLs<T, N>() } as HndlrsLs<T, N>
        ls.handleAll(this as T, old, new)
    }

    override suspend fun <N> Mutex.withTransferredLockHndlChange(
        prop: ObservableProperty<T, N>,
        f1: suspend () -> Observable.OldNewPair<N>?,
    ) {
        this.lock()
        val pair = f1.invoke()
        pair?.let { hndlMtx.lock() }
        this.unlock()
        pair?.let {
            val ls = hndlrs.getOrPut(prop) { HndlrsLs<T, N>() } as HndlrsLs<T, N>
            val (old, new) = pair
            ls.handleAll(this as T, old, new)
            hndlMtx.unlock()
        }
    }
}
