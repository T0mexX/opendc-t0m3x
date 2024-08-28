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

package org.opendc.simulator.network.components

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.ifNull0
import org.opendc.simulator.network.utils.logger

/**
 * Node you can start a [NetFlow] from or direct a [NetFlow] to.
 */
internal interface EndPointNode : Node {
    companion object {
        private val log by logger()
    }

    /**
     * Starts a [NetFlow] from ***this*** node.
     * @param[newFlow]  the [NetFlow] to be started.
     */
    suspend fun startFlow(newFlow: NetFlow) {
        if (newFlow.transmitterId != this.id) {
            log.error("unable to start flow, this node is not the sender, aborting...")
            return
        }

        with(flowHandler) { generateFlow(newFlow) }
    }

    /**
     * Stops [NetFlow] with id [fId] if it is generated by ***this***. Else logs error.
     */
    suspend fun stopFlow(fId: FlowId) {
        with(flowHandler) { stopGeneratedFlow(fId) }
    }

    /**
     * Stores a reference to an incoming [NetFlow] so that its end-to-end data rate can be updated.
     * @param[f]  the [NetFlow] to store the reference of.
     */
    fun addReceivingEtoEFlow(f: NetFlow) {
        flowHandler.addConsumingFlow(f)
    }

    /**
     * Removes the reference of an incoming [NetFlow]. To be called when the end-to-end
     * flow is no longer running through the network.
     * @param[flowId]   id of the end-to-end flow whose reference is to be removed.
     */
    fun rmReceivingEtoEFlow(flowId: FlowId) {
        flowHandler.rmConsumingFlow(flowId)
    }

    override fun totIncomingDataRateOf(fId: FlowId): DataRate =
        with(flowHandler) {
            if (fId in generatingFlows) {
                DataRate.ZERO
            } else {
                consumingFlows[fId]?.throughput
                    ?: let { outgoingFlows[fId]?.demand }
                        .ifNull0()
            }
        }
}
