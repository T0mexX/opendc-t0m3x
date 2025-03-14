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

package org.opendc.simulator.network.api.integration

import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.NetSimScope

public class JNetFlow internal constructor(
    internal val f: NetFlow,
    private val scope: NetSimScope,
) {
    public val id: FlowId get() = f.id
    public val srcId: NodeId get() = f.srcId
    public val destId: NodeId get() = f.destId

    public fun setDemand(
        demandKbps: Double,
        fragId: Any,
    ): Unit =
        latched(scope) {
            f.msgAsyncSetDemand(DataRate.ofKbps(demandKbps), fragId)
        }

    public fun fragInit(
        targetKb: Double,
        fragId: Any,
    ): Unit =
        latched(scope) {
            f.msgAsyncFragInit(DataSize.ofKb(targetKb), fragId)
        }
}
