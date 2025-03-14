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
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt

@Serializable
@SerialName("first_come_first_served")
internal data object FirstComeFirstServed : FairnessPolicy {
    context(FlowHandler)
    override fun applyPolicy(updt: RateUpdt) {
        execRateReductions(updt)

//        val flows = nodeFlowTracker[UnsatisfiedByDemand]

//        outgoingFlows.values.forEach {
//            val oldTput = it.totRateOut
//            if (it.tryUpdtRate() == oldTput) return
//        }

        updt.filter {
            it.value approxLarger DataRate.zero
        }.toList().forEach { (flowId, _) ->
            outgoingFlows[flowId]
                ?.tryUpdtRate()
        }
    }
}
