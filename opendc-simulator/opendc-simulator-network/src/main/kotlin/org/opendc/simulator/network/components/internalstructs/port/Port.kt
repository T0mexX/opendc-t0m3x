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

package org.opendc.simulator.network.components.internalstructs.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.flow.FlowId

internal interface Port {
    val maxSpeed: DataRate
    val owner: Node

    var sendLink: SendLink?
    var receiveLink: ReceiveLink?

    /**
     * `true` if this port is connected in any direction to another port, `false` otherwise.
     */
    val isConnected: Boolean
    val incomingRatesById: Map<FlowId, DataRate>
    val outgoingRatesById: Map<FlowId, DataRate>
    val maxPortToPortBW: DataRate
    val nodeUpdtChl: UpdateChl
    val isActive: Boolean
    val util: Double
    val currSpeed: DataRate
    val otherEndPort: Port?
    val otherEndNode: Node?

    suspend fun notifyReceiver()

    fun incomingRateOf(fId: FlowId): DataRate

    fun outgoingRateOf(fId: FlowId): DataRate

    /**
     * Tries to update the flow corresponding to [fId] to the requested [targetRate].
     * @return the actual data rate achieved for the flow.
     */
    fun tryUpdtRateOf(
        fId: FlowId,
        targetRate: DataRate,
    ): DataRate
}
