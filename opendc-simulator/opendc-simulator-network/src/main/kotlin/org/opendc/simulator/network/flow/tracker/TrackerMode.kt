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

package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.OutFlow
import java.util.TreeSet

internal interface TrackerMode {
    fun OutFlow.compare(other: OutFlow): Int

    fun OutFlow.shouldBeTracked(): Boolean

    companion object {
        /**
         * Sets up the [TreeSet] of [OutFlow]s ([allOutgoingFlows]) to track in a specific node
         * (e.g. useful for fairness policies), based on the [compare] rule defined in concrete instances.
         *
         * This function is called whenever a new tracker is requested.
         */
        fun TrackerMode.setUp(allOutgoingFlows: Map<FlowId, OutFlow>): TreeSet<OutFlow> {
            val treeSet =
                TreeSet<OutFlow> { a, b ->
                    if (a === b) {
                        0
                    } else {
                        a.compare(other = b).let { outerIt ->
                            if (outerIt == 0) {
                                (a.hashCode() - b.hashCode()).let {
                                    if (it == 0) {
                                        System.identityHashCode(a) - System.identityHashCode(b)
                                    } else {
                                        it
                                    }
                                }
                            } else {
                                outerIt
                            }
                        }
                    }
                }

            treeSet.addAll(allOutgoingFlows.values)
            return treeSet
        }
    }
}

internal object AllByDemand : TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int = this.demand.compareTo(other.demand)

    override fun OutFlow.shouldBeTracked(): Boolean = true
}

internal object AllByOutput : TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int = this.totRateOut.compareTo(other.totRateOut)

    override fun OutFlow.shouldBeTracked(): Boolean = true
}

internal object UnsatisfiedByRateOut : TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int = this.totRateOut.compareTo(other.totRateOut)

    override fun OutFlow.shouldBeTracked(): Boolean = demand.approxLarger(totRateOut)
}

internal object UnsatisfiedByDemand : TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int = this.demand.compareTo(other.demand)

    override fun OutFlow.shouldBeTracked(): Boolean = demand.approxLarger(totRateOut)
}

internal object AllByUnsatisfaction : TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int = (this.totRateOut / this.demand).compareTo(other.totRateOut / other.demand)

    override fun OutFlow.shouldBeTracked(): Boolean = true
}
