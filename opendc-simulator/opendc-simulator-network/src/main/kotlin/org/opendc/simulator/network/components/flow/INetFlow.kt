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
import org.opendc.common.units.DataSize
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.integration.JNetFlow
import org.opendc.simulator.network.components.NetRunnable
import org.opendc.simulator.network.components.invalidatable.IInvalidatable
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.Msgable
import org.opendc.simulator.network.components.msgable.ReqMsg
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.synchronizable.Synchronizable
import org.opendc.simulator.network.simscope.fwpool.FWId

internal interface INetFlow : NetFlow, Msgable<INetFlow>, NetRunnable, IInvalidatable, Synchronizable<INetFlow> {
    var senderNode: SenderNode<*>
    var jNetFlow: JNetFlow?

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Msg Convenience Methods
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    suspend fun msgAsyncSetTput(newTput: DataRate)

    suspend fun msgAsyncIncreaseTputBy(amount: DataRate)

    suspend fun msgSyncReqFragComplEstimate(): Timestamp

    interface SetThroughput : Msg<INetFlow, SetThroughput> {
        var newTput: DataRate

        companion object : FWId<SetThroughput>
    }

    interface IncreaseThroughput : Msg<INetFlow, IncreaseThroughput> {
        var amount: DataRate

        companion object : FWId<IncreaseThroughput>
    }

    interface SetDemand : Msg<INetFlow, SetDemand> {
        var newDemand: DataRate
        var fragId: Any?

        companion object : FWId<SetDemand>
    }

    interface ReqFragComplEstimate : ReqMsg<INetFlow, Timestamp, ReqFragComplEstimate> {
        companion object : FWId<ReqFragComplEstimate>
    }

    interface FragInit : Msg<INetFlow, FragInit> {
        var fragTarget: DataSize
        var fragId: Any?

        companion object : FWId<FragInit>
    }
}
