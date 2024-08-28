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

package org.opendc.simulator.network.flow

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.utils.ifNull0

/**
 * Represents a data rate demand update in terms of delta rate by id.
 * These updates are sent into the [UpdateChl] of the [Node] to be processed.
 *
 * An update with delta rates of 0 will reapply the fairness policy of the [Node].
 * Needed in case the topology changes.
 *
 * A value class offers higher type safety than a typealias with extension functions.
 */
@JvmInline
internal value class RateUpdt(private val updt: Map<FlowId, DataRate>) : Map<FlowId, DataRate> by updt {
    constructor(p: Pair<FlowId, DataRate>) : this(mapOf(p))
    constructor(fId: FlowId, deltaRate: DataRate) : this(mapOf(fId to deltaRate))

    companion object {
        fun Map<FlowId, DataRate>.toRateUpdt(): RateUpdt = RateUpdt(this)

        @JvmName("toRateUpdtFromMutable") // otherwise methods have same signature on the jvm
        fun MutableMap<FlowId, DataRate>.toRateUpdt(): RateUpdt = RateUpdt(this.toMap())
    }

    fun merge(other: RateUpdt): RateUpdt =
        RateUpdt(
            (updt.keys + other.updt.keys)
                .associateWith {
                    ((updt[it].ifNull0()) + (other.updt[it].ifNull0()))
                },
        )
}
