/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.input

import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.api.workload.SimNetWorkload
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.flow.FlowId
import kotlin.properties.Delegates

/**
 * Contains all the information needed to create a network event **except** knowledge about previous events.
 * Traces are first converted to event imprints and then to [SimNetWorkload] after they have been ordered by timestamp,
 * since dataframe offer no guarantee on the order of fragments.
 */
internal data class NetEventImprint(
    val deadline: Time,
    val transmitterId: NodeId,
    val destId: NodeId,
    val netTx: DataRate,
    val flowId: FlowId? = null,
    val duration: Time? = null,
) {
    class Builder {
        var deadline by Delegates.notNull<Time>()
        var transmitterId by Delegates.notNull<NodeId>()
        var destId by Delegates.notNull<NodeId>()
        var netTx by Delegates.notNull<DataRate>()
        var flowId: FlowId? = null
        var duration: Time? = null

        fun build(): NetEventImprint =
            NetEventImprint(
                deadline = deadline,
                transmitterId = transmitterId,
                destId = destId,
                netTx = netTx,
                flowId = flowId,
                duration = duration,
            )

        fun reset() {
            flowId = null
            duration = null
            transmitterId = INTERNET_ID
            destId = INTERNET_ID
        }
    }
}
