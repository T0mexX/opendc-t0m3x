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
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.RateUpdt.Companion.toRateUpdt
import org.opendc.simulator.network.utils.ifNull0

internal class SimplexLink(
    private val senderP: Port,
    private val receiverP: Port,
    override val linkBW: DataRate = senderP.maxSpeed min receiverP.maxSpeed,
) : SendLink, ReceiveLink {
    override var usedBW: DataRate = DataRate.ZERO
        private set

    override val util: Double
        get() =
            (usedBW / maxPort2PortBW).let {
                check(it in .0..1.0)
                it
            }

    override var maxPort2PortBW: DataRate = minOf(linkBW, senderP.currSpeed, receiverP.currSpeed)
        private set

    private val rateById = mutableMapOf<FlowId, DataRate>()
    override val incomingRateById: Map<FlowId, DataRate> get() = rateById
    override val outgoingRatesById: Map<FlowId, DataRate> get() = rateById

    private val currLinkUpdate = mutableMapOf<FlowId, DataRate>()

    override fun notifyPortSpeedChange() {
        maxPort2PortBW = minOf(senderP.currSpeed, receiverP.currSpeed, linkBW)
        TODO()
    }

    override fun Port.updtFlowRate(
        fId: FlowId,
        rqstRate: DataRate,
    ): DataRate =
        rateById.compute(fId) { _, oldRate ->
            if (this !== senderP) throw RuntimeException()

            val wouldBeDeltaBW: DataRate = rqstRate - (oldRate.ifNull0())
            val wouldBeUsedBW = usedBW + wouldBeDeltaBW

            // handles case max bandwidth is reached,
            // reducing the bandwidth increase to the maximum available
            val newRate: DataRate =
                if (wouldBeUsedBW > maxPort2PortBW) {
                    (rqstRate - (wouldBeUsedBW - maxPort2PortBW)).roundToIfWithinEpsilon(DataRate.ZERO)
                } else {
                    rqstRate
                }

            val deltaBw = (newRate - oldRate.ifNull0()).roundToIfWithinEpsilon(DataRate.ZERO)
            if (deltaBw.isZero()) return@compute oldRate.ifNull0()

            // Updates the current link bandwidth usage
            usedBW += deltaBw

            if (deltaBw != DataRate.ZERO) {
                currLinkUpdate.compute(fId) { _, oldDelta ->
                    val newFlowDelta = (deltaBw + (oldDelta.ifNull0())).roundToIfWithinEpsilon(DataRate.ZERO)
                    if (newFlowDelta.isZero()) {
                        null
                    } else {
                        newFlowDelta
                    }
                }
            }

            return@compute newRate
        }!!

    // Only called by sender which has the SendLink interface.
    override fun outgoingRateOf(fId: FlowId): DataRate = rateById[fId].ifNull0()

    // Only called by receiver which has the ReceiveLink interface.
    override fun incomingRateOf(fId: FlowId): DataRate = rateById[fId].ifNull0()

    /**
     * Returns the [Port] on the opposite side of the [SimplexLink].
     * @param[p]    port of which the opposite is to be returned.
     */
    override fun oppositeOf(p: Port): Port {
        return when {
            p === senderP -> receiverP
            p === receiverP -> senderP
            else -> throw IllegalArgumentException(
                "Non link port of ${p.owner} requesting its opposite. " +
                    "This link is between ${senderP.owner} and ${receiverP.owner}",
            )
        }
    }

    override suspend fun notifyReceiver() {
        // channel has unlimited size => should never suspend
        if (currLinkUpdate.isEmpty()) return

        // TODO: change
        receiverP.nodeUpdtChl.send(
            currLinkUpdate.toRateUpdt(),
        )
        currLinkUpdate.clear()
    }
}
