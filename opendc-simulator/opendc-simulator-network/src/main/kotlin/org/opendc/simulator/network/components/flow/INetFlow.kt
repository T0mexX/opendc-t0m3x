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

package org.opendc.simulator.network.components.flow

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.Launchable
import org.opendc.simulator.network.components.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.Msgable
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.simscope.fwpool.FWId

internal interface INetFlow : NetFlow, Msgable<INetFlow>, Launchable, IInvalidatable {
    var senderNode: SenderNode<*>

    suspend fun msgAsyncSetTput(newTput: DataRate)

    suspend fun msgAsyncIncreaseTputBy(amount: DataRate)

    interface SetThroughput : Msg<INetFlow, SetThroughput> {
        var newThroughput: DataRate

        companion object : FWId<SetThroughput>
    }

    interface IncreaseThroughput : Msg<INetFlow, IncreaseThroughput> {
        var amount: DataRate

        companion object : FWId<IncreaseThroughput>
    }

    interface SetDemand : Msg<INetFlow, SetDemand> {
        var newDemand: DataRate

        companion object : FWId<SetDemand>
    }
}
