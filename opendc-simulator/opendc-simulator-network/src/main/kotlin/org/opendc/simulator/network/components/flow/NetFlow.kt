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
import org.opendc.simulator.network.api.integration.NetFTracker
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NonOwnerMethod
import org.opendc.simulator.network.components.evntemitter.Evnt
import org.opendc.simulator.network.components.evntemitter.EvntEmitter
import org.opendc.simulator.network.components.invalidatable.Invalidatable
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.fwpool.FWId
import org.opendc.simulator.network.utils.InternalODCNetworkApi

/**
 * Represents a unidirectional network flow between two [Node]s.
 */
public interface NetFlow : Invalidatable, EvntEmitter<NetFlow> {
    /**
     * Unique id for this flow.
     */
    public val id: FlowId

    /**
     * The id of the source node.
     */
    public val srcId: NodeId

    /**
     * The id of the destination node.
     */
    public val destId: NodeId

    /**
     * The current demand
     */
    @InternalODCNetworkApi
    public val demand: DataRate

    @InternalODCNetworkApi
    public val throughput: DataRate

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Convenience Message Methods
    // //// Used for convenience instead of manually acquire, fill, and send flyweight message objects.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Convenience method to send a [INetFlow.SetDemand]~[Msg] to this flow.
     * @see INetFlow.SetDemand
     */
    @NonOwnerMethod(callableBy = [NetCo.EXTERNAL, NetCo.MAIN])
    public suspend fun msgAsyncSetDemand(
        demand: DataRate,
        fragId: Any? = null,
    )

    /**
     * Convenience method to send a [INetFlow.FragInit]~[Msg] to this flow.
     * @see INetFlow.FragInit
     */
    @NonOwnerMethod(callableBy = [NetCo.EXTERNAL, NetCo.MAIN])
    public suspend fun msgAsyncFragInit(
        target: DataSize,
        fragId: Any? = null,
    )

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Event emitted when the end-to-end throughput changes during simulation.
     * @property f The flow whose throughput changed.
     * @property old The old throughput.
     * @property new The new throughput.
     * @property newComplEstimate The new time remaining to complete the current fragment [TimeDelta.zero]
     * if no target [DataSize] was set for the current fragment.
     * For now, only used in compute-network combined simulation.
     *
     * @see Evnt
     * @see NetFTracker
     * @see INetFlow.msgAsyncFragInit
     */
    public abstract class TPutChanged : Evnt<NetFlow, TPutChanged>(), Invalidatable {
        public abstract var f: NetFlow
        public abstract var old: DataRate
        public abstract var new: DataRate
        public abstract var newComplEstimate: Timestamp

        override fun toString(): String = "TPutChanged(f=${f.id}, old=$old, new=$new)"

        public companion object : FWId<TPutChanged>
    }

    /**
     * Event emitted when the timestamp at which the fragment is expected to be completed changes.
     * @property f The flow whose completion estimate changed.
     * @property new The new estimated timestamp for to complete the current fragment [TimeDelta.zero]
     * if no target [DataSize] was set for the current fragment.
     * For now, only used in compute-network combined simulation.
     *
     * @see Evnt
     * @see NetFTracker
     * @see INetFlow.msgAsyncFragInit
     */
    public abstract class FragComplEstimateChanged : Evnt<NetFlow, FragComplEstimateChanged>(), Invalidatable {
        public abstract var f: NetFlow
        public abstract var old: Timestamp
        public abstract var new: Timestamp
        public abstract var fragId: Any?

        public companion object : FWId<FragComplEstimateChanged>
    }

    /**
     * Event emitted when the current fragment target [DataSize]
     * has been transmitted by this flow.
     *
     * @see Evnt
     * @see NetFTracker
     * @see INetFlow.msgAsyncFragInit
     */
    public abstract class FragCompl : Evnt<NetFlow, FragCompl>(), Invalidatable {
        public abstract var f: NetFlow
        public abstract var fragId: Any?

        public companion object : FWId<FragCompl>
    }
}
