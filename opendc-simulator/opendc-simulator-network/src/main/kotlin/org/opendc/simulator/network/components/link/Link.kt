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

package org.opendc.simulator.network.components.link

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.internalstructs.port.Port

internal interface Link {
    val linkBW: DataRate
    val maxPort2PortBW: DataRate
    val usedBW: DataRate
    val util: Double
    val availableBW: DataRate get() = maxPort2PortBW - usedBW

    fun oppositeOf(p: Port): Port

    fun notifyPortSpeedChange()

//    companion object {
//
//        fun connectPorts(portA: Port, portB: Port, duplex: Boolean = true, linkBW: DataRate? = null) {
//            require(portA.isConnected.not() && portB.isConnected.not())
//            { "unable to connect ports $portA and $portB. One of the ports is already connected" }
//
//            val computedLinkBW: DataRate = linkBW ?: (min(portA.maxSpeed, portB.maxSpeed))
//
//            val a2b = SimplexLink(senderP = portA, receiverP = portB, linkBW = computedLinkBW)
//            portA.connectLink(a2b as SendLink)
//            portB.connectLink(a2b as ReceiveLink)
//
//            if (duplex) {
//                val b2a = SimplexLink(senderP = portB, receiverP = portA, linkBW = computedLinkBW)
//                portA.connectLink(b2a as ReceiveLink)
//                portB.connectLink(b2a as SendLink)
//            }
//        }
//    }
}
