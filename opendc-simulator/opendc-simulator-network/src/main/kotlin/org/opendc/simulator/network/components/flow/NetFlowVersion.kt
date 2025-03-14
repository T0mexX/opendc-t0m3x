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

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.fwpool.FWDispenser

@Serializable
internal sealed interface NetFlowVersion {
    context(NetSimScope)
    suspend operator fun invoke(
        srcId: NodeId,
        destId: NodeId,
        id: FlowId? = null,
        dmnd: DataRate = DataRate.zero,
    ): INetFlow

    val setDemandDisp: FWDispenser<INetFlow.SetDemand>

//    val demandChangedDisp: FWDispenser<NetFlow.DemandChanged>
//
    val tputChangedDisp: FWDispenser<NetFlow.TPutChanged>
//
//    val fragmentCompletedDisp: FWDispenser<NetFlow.FragmentCompleted>

    val setTputDisp: FWDispenser<INetFlow.SetThroughput>

    val increaseTputDisp: FWDispenser<INetFlow.IncreaseThroughput>

    val reqTmRmDisp: FWDispenser<INetFlow.ReqTmRm>

    val fragInitDisp: FWDispenser<INetFlow.FragInit>

    val fragComplDisp: FWDispenser<NetFlow.FragCompl>

    val tmRmChangedDisp: FWDispenser<NetFlow.TmRmChanged>

    context(NetSimScope)
    suspend fun initDispensers()
}
