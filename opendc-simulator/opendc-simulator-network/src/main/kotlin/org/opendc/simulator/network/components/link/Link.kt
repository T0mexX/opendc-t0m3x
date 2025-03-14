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

package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NetCoOwner
import org.opendc.simulator.network.components.NonOwnerMethod
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.invalidatable.IInvalidatable
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * Interface for a unidirectional network communication link from the sender perspective,
 * used to send messages and manage bandwidth between nodes.
 *
 * This interface does not manage per-flow bandwidth allocations.
 * Instead, it focuses on the overall utilization of the link.
 *
 * A [Link] can also be used as a [Msg] [Channel] to send msgs directly to the receiver nodes.
 */
@NetCoOwner(owner = NetCo.NODE, additionalInfo = "The node that sends data through this link is the owner")
internal interface Link : SendChannel<Msg<Node<*>, *>>, IInvalidatable {
    /**
     * The node connected to the receiver end of this link.
     */
    val receiverN: Node<*>

    /**
     * The maximum bandwidth capacity of this link.
     */
    val maxBw: DataRate

    /**
     * The amount of bandwidth currently unused and available for allocation.
     */
    val availableBw: DataRate

    /**
     * The current utilization of the link, expressed as a percentage of [maxBw].
     */
    val util: Percentage

    /**
     * The sum of the tentative tx data rates for all flows.
     */
    val totTentativeTx: DataRate

    /**
     * The index of this link in the [Node.links] owner node array.
     * Used for efficient identification and lookup.
     */
    val linkIdx: Int

    /**
     * This method is responsible for actually sending
     * [Node.RxUpdt]~[Msg]s to the adjacent nodes in quick succession.
     *
     * Actual throughput through the link for a flow is proportional to that
     * flow tentative tx set through [setTentativeTx], up to a total of [maxBw].
     */
    context(NetSimScope)
    suspend fun attemptTx()

    /**
     * The owner node sets the desired data rate ([dr]) for flow [f]
     * according to some [RoutPolicy] to attempt to transmit at
     * the next invocation of [attemptTx].
     */
    suspend fun setTentativeTx(
        dr: DataRate,
        f: INetFlow,
        entryId: Int? = null,
    ): Int

    /**
     * Retrieves the actual throughput through the link for flow corresponding to [entryId].
     */
    @NonOwnerMethod(callableBy = [NetCo.MAIN], additionalInfo = "currently used during export of node information")
    fun getTx(entryId: Int): DataRate
}
