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

/**
 * Classes implementing this interface provide
 * methods to retrieve flow information.
 */
internal interface FlowView {
    /**
     * Returns the total incoming data rate corresponding
     * to [fId] (generated flows are not included).
     */
    fun totIncomingDataRateOf(fId: FlowId): DataRate

    /**
     * Returns the total outgoing data rate corresponding to [fId] (not the throughput).
     */
    fun totOutgoingDataRateOf(fId: FlowId): DataRate

//    /**
//     * Returns the outgoing throughput of the flow corresponding to [flowId].
//     */
//    fun throughputOutOf(flowId: FlowId): DataRate

    /**
     * Returns the [FlowId]s of all flows transiting
     * through ***this*** (including generated flows).
     */
    fun allTransitingFlowsIds(): Collection<FlowId>

    /**
     * Returns a formatted [String] representation of all the flows
     * that transit through ***this***, both incoming and outgoing.
     */
    fun fmtFlows(): String =
        buildString {
            appendLine("| ==== Node Flows ====")
            appendLine(
                "| " +
                    "id".padEnd(15) +
                    "in".padEnd(15) +
                    "out".padEnd(15),
            )
            allTransitingFlowsIds().forEach { flowId ->
                appendLine(
                    "| " +
                        flowId.toString().padEnd(15) +
                        totIncomingDataRateOf(flowId).fmtValue("%.5f").padEnd(15) +
                        totOutgoingDataRateOf(flowId).fmtValue("%.5f").padEnd(15),
                )
            }
        }
}
