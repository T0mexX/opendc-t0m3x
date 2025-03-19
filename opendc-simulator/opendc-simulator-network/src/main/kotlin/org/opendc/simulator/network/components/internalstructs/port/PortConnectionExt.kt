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

import org.opendc.common.logger.logger
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.LinkFlowUpdate
import org.opendc.simulator.network.components.link.SimplexLink
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flowOld.RateUpdt
import org.opendc.simulator.network.flowOld.RateUpdt.Companion.toRateUpdt
import org.opendc.simulator.network.simscope.NetSimScope

private val log by Unit.logger("PortConnectionExt")

//internal suspend fun Port.connect(
//    other: Port,
//    linkBW: DataRate? = null,
//) = this.connect(other = other, linkBW = linkBW, notifyOther = true)

//context(NetSimScope)
//private suspend fun Port.connect(
//    other: Port,
//    linkBW: DataRate?,
//    notifyOther: Boolean,
//) {
//    require(this.txLink == null) { "unable to connect ports $this and $other. $this is already connected" }
//
//    val computedLinkBW: DataRate = linkBW ?: (this.speed min other.speed)
//
//    val thisToOther = SimplexLink(other.owner.notificationChl, maxBw = computedLinkBW)
//    this.txLink = thisToOther
//    other.rxLink = thisToOther
//
//    val notif = poolAggr.getOrAdd(Port.Connect) {
//        TODO()
//    }.dispenser().acquire()
//    notif.other = this
//
////    if (duplex && notifyOther) {
//    runCatching { other.connect(other = this, duplex = true, linkBW = computedLinkBW, notifyOther = false) }
//        .also {
//            if (it.isFailure) {
//                // disconnects the established 1 way link before rethrowing exception
//                this.disconnect()
//                throw it.exceptionOrNull()!!
//            }
//        }
//    }
//}

internal suspend fun Port.disconnect() {
    if (this.isConnected.not()) return log.warn("unable to disconnect port $this, port not connected")

    val update: RateUpdt? = receiveLink?.incomingRateById?.mapValues { (_, rate) -> -rate }?.toRateUpdt()

    sendLink = null
    receiveLink = null

    // should not suspend since channel has unlimited capacity
    if (update?.isNotEmpty() == true) {
        update.let { nodeUpdtChl.send(it) }
    }
}
