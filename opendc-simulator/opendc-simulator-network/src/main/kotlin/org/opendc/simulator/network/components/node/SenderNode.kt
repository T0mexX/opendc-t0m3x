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

package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal abstract class SenderNode<Self : SenderNode<Self>>(
    ip: IPv4Address,
    nPorts: Int,
) : NodeImpl<Self>(ip, nPorts) {
    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun startFlow(netF: INetFlow) {
        assert(netF.demand >= DataRate.zero)
        assert(netF.destId != this.id)
        assert(netF.senderId == this.id)

        // TODO: maybe check that flow does not exist
        netF.netLaunch()
        netF.senderNode = this

        if (netF.demand.isZero()) return

        nodeVersion.rxUpdateDisp.acquire().reset {
            this.deltaRate = netF.demand
            this.netF = netF
        }.sendTo(this)
    }

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun stopFlow(netF: INetFlow) {
        flowTable.reset(netF)
    }
}
