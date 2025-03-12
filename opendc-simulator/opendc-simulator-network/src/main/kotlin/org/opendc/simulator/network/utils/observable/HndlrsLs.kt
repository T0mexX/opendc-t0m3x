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

package org.opendc.simulator.network.utils.observable

import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Key.getSeqChl
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import org.opendc.simulator.network.utils.ChangeHndlr
import org.opendc.simulator.network.utils.SusChangeHndlr
import org.opendc.simulator.network.utils.lazy
import kotlin.coroutines.coroutineContext

internal class HndlrsLs<O, T>(private val mode: Mode = Mode.BOTH) {
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
            val chl = coroutineContext.getSeqChl()
            seq.forEach { chl.send(it.lazy(obj, old, new)) }
        }
        if (mode == Mode.SUSPENDING || mode == Mode.BOTH) {
            sus.forEach { it.handle(obj, old, new) }
        }
    }
}
