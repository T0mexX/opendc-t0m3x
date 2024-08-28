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

package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.tracker.AllByDemand
import org.opendc.simulator.network.utils.ifNull0
import org.opendc.simulator.network.utils.isSorted

@Serializable
@SerialName("max-min")
internal data object MaxMinPerPort : FairnessPolicy {
    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        resetAll()

        if (FairnessPolicy.VERIFY) {
            check(ports.all { it.availableTxBW() approx it.maxPortToPortBW }) {
                println(ports.filter { it.isConnected }.map { it.availableTxBW() })
            }
        }

        // All flows sorted by demand.
        val flows: List<OutFlow> = nodeFlowTracker[AllByDemand]
        if (FairnessPolicy.VERIFY) check(flows.isSorted { a, b -> a.demand.compareTo(b.demand) })

        // Each port of the node associated with the number of flows
        // that need to traverse it.
        val remainingFlowsPerPort: MutableMap<Port, Int> =
            buildMap {
                flows.forEach { flow ->
                    flow.outRatesByPort.keys.forEach { port ->
                        compute(port) { _, oldValue ->
                            (oldValue ?: 0) + 1
                        }
                    }
                }
            }.toMutableMap()

        // Max-min for each port multiple ports.
        flows.forEachIndexed { idx, outFlow ->
            val targetPorts: Collection<Port> = outFlow.outRatesByPort.keys
            val flowDmPerPort: DataRate = outFlow.demand / targetPorts.size

            targetPorts.forEach { port ->
                val remFlowsForThisPort: Int = remainingFlowsPerPort[port]!!
                val availableShare: DataRate = port.availableTxBW() / remFlowsForThisPort
                val targetRate: DataRate = availableShare min flowDmPerPort

                val newRate: DataRate = outFlow.tryUpdtPortRate(port, targetRate)
                check(newRate approx targetRate) { "MaxMin policy error" }

                remainingFlowsPerPort[port] = remFlowsForThisPort - 1
            }
        }

        if (FairnessPolicy.VERIFY) {
            check(remainingFlowsPerPort.values.all { it == 0 })
            verify()
        }
    }
}

private fun Port.availableTxBW(): DataRate = sendLink?.availableBW.ifNull0()

private fun FlowHandler.resetAll() {
    outgoingFlows.values.forEach {
        check(it.tryUpdtRate(DataRate.ZERO).isZero()) { "${it.id}" }
    }
}

private fun FlowHandler.verify() {
    fun OutFlow.passesThrough(port: Port): Boolean = port in outRatesByPort.keys

    val flows: List<OutFlow> = nodeFlowTracker[AllByDemand]

    flows.forEach { it.verify() }

    ports.forEach { p ->
        var prevOut: DataRate = DataRate.ZERO

        flows.forEach { f ->
            if (f.passesThrough(p)) {
                val currOut: DataRate = p.outgoingRateOf(f.id)

                check(currOut approxLargerOrEq prevOut) { "MaxMin policy error " }

                prevOut = currOut
            }
        }
    }
}
