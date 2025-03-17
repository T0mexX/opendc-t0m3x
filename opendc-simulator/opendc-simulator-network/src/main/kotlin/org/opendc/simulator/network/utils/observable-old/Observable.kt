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

package org.opendc.simulator.network.utils.`observable-old`

import org.apache.yetus.audience.InterfaceAudience.Public
import org.opendc.common.annotations.InternalUse
import org.opendc.simulator.network.utils.`observable-old`.elements.ObservableEvent
import org.opendc.simulator.network.utils.`observable-old`.elements.ObservableProperty
import org.opendc.simulator.network.utils.`observable-old`.handlers.EventHndlr
import org.opendc.simulator.network.utils.`observable-old`.handlers.SusEventHndlr

@Public
public interface Observable<T : Observable<T>> {

    @InternalUse
    public fun setUpDelegatedObservable(delegator: T)

    public suspend fun <N> withChangeHndlr(
        prop: ObservableProperty<T, N>,
        hndlr: SusChangeHndlr<T, N>,
    ): T

    public fun <N> withChangeHndlrSeq(
        prop: ObservableProperty<T, N>,
        hndlr: ChangeHndlr<T, N>,
    ): T

    public suspend fun withEventHndlr(
        event: ObservableEvent<T>,
        hndlr: SusEventHndlr<T>,
    ): T

    public fun withEventHndlrSeq(
        event: ObservableEvent<T>,
        hndlr: EventHndlr<T>,
    ): T

    @InternalUse
    public suspend fun <N> triggerHandlers(
        prop: ObservableProperty<T, N>,
        old: N,
        new: N,
    )

    @InternalUse
    public suspend fun triggerHandlers(
        event: ObservableEvent<T>,
    )
}
