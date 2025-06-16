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

package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.link.LinkEntry

/**
 * TODO
 */
@Serializable
@SerialName("fcfs")
internal class FirstComeFirstServed : FairnessPolicy() {
    context(Link)
    override suspend fun applyFairness(entryList: List<LinkEntry>) {
        TODO()
//        val p = this@Port
//        val l = p.txLink!!
//
//        processDemandReductions(entryList)
//
//        coroutineScope {
//            entryList.asFlow().onEach {
//                if (it.used.not()) return@onEach
//                val increaseBy = it.demand - it.tput
//                assert(increaseBy >= DataRate.zero) { increaseBy.value }
//                if (increaseBy approx  DataRate.zero) return@onEach
//                val claimedBw = l.claimBw(increaseBy, it.netF)
//                if (claimedBw approx  DataRate.zero) return@onEach
//                it.tput = (it.tput + claimedBw).roundToIfWithinEpsilon(it.demand, epsilon = 1.0)
//                assert(it.tput <= it.demand)
//            }.launchIn(this@coroutineScope)
    }
}
