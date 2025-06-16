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
import org.opendc.simulator.network.components.evntemitter.Evnt
import org.opendc.simulator.network.components.evntemitter.EvntEmitter
import org.opendc.simulator.network.components.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.components.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.fwpool.FWId

public interface NetFlow : Invalidatable, EvntEmitter<NetFlow> {
    public val id: FlowId
    public val senderId: NodeId
    public val destId: NodeId
    public val demand: DataRate
    public val throughput: DataRate

    public suspend fun setDemand(demand: DataRate)

    /**
     * Hashing based on [id].
     */
    override fun hashCode(): Int

    /**
     * Equality based on [id].
     */
    override fun equals(other: Any?): Boolean

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public abstract class TPutChanged : Evnt<NetFlow, TPutChanged>(), IInvalidatable {
        public abstract var netFlow: NetFlow
        public abstract var old: DataRate
        public abstract var new: DataRate

        override fun toString(): String = "TPutChanged(netFlow=${netFlow.id}, old=$old, new=$new)"

        public companion object : FWId<TPutChanged>
    }

    public abstract class FragmentCompleted : Evnt<NetFlow, FragmentCompleted>() {
        public abstract var netFlow: NetFlow

        public companion object : FWId<FragmentCompleted>
    }
}
