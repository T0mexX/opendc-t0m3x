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

package org.opendc.simulator.network.simscope

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import org.opendc.common.units.Energy
import org.opendc.common.units.Power
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.simulator.network.utils.sync.Synchronizable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class NetSimEnRecorder(
    tmSrc: NetSimTmSrc<*>,
) : Synchronizable<NetSimEnRecorder>, AbstractCoroutineContextElement(Key) {
    private var avrgPwrDraw: Power = Power.zero
    private var currPwrDraw: Power = Power.zero
    private var totEnCons: Energy = Energy.zero
    override var lastSync: Timestamp = tmSrc.initialTmStamp

    /**
     * TODO
     * > If suspending getters and setters are ever included in kotlin, replace this with suspending getter.
     */
    context(NetSimScope)
    suspend fun getAvrgPwrDraw(): Power = barrier.whileStable { sync().avrgPwrDraw }

    /**
     * TODO
     * > If suspending getters and setters are ever included in kotlin, replace this with suspending getter.
     */
    context(NetSimScope)
    suspend fun getTotEnCons(): Energy = barrier.whileStable { sync().totEnCons }

    /**
     * TODO
     * > If suspending getters and setters are ever included in kotlin, replace this with suspending getter.
     */
    context(NetSimScope)
    suspend fun getCurrPwrDraw(): Power = barrier.whileStable { sync().currPwrDraw }

    /**
     * TODO
     */
    context(NetSimScope)
    override suspend fun sync(forceUpdt: Boolean): NetSimEnRecorder {
        if (isSync() && forceUpdt.not()) return this

        // Time passed since last synchronization.
        val sinceSync = tmSrc.tmstamp timeDelta lastSync
        check(sinceSync >= TimeDelta.zero)

        // Time passed since the start of simulation virtual time.
        val sinceStart = tmSrc.sinceStart
        check(sinceStart >= TimeDelta.zero)

        // Time passed from the start of simulation virtual time up to the last synchronization.
        val startToSync = sinceStart - sinceSync
        check(startToSync >= TimeDelta.zero)

        barrier.whileStable {
            currPwrDraw = compCurrPwrDraw()

            // Update total energy consumption.
            totEnCons += currPwrDraw * sinceSync

            // Update average power usage.
            avrgPwrDraw = (
                (
                    (avrgPwrDraw * startToSync) +
                        currPwrDraw * sinceSync
                ) / sinceStart
            ).takeIf { it.value.isNaN().not() } ?: Power.zero

            lastSync = tmSrc.tmstamp
        }

        return this
    }

    /**
     * TODO
     */
    context(NetSimScope)
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun compCurrPwrDraw(): Power =
        this@NetSimScope.async {
            barrier.whileStable {
                net.nodesById.values.asFlow().filterIsInstance<EnConsumer<*>>().flatMapMerge {
                    flow { emit(it.computePwrDraw()) }
                }.fold(Power.zero) { acc, partial -> acc + partial }
            }
        }.await()

    /**
     * @return a formatted [String] that displays energy consumption and power draw of the network.
     * Preferably to be logged on a new line.
     */
    internal fun fmt(flags: Flags<NetSimEnRecorder> = Flags.all()): String =
        buildString {
            appendLine("| ==== Energy Report ====")
            flags.ifSet(PWR_DRAW) { appendLine("| Current Power Draw: $currPwrDraw") }
            flags.ifSet(AVG_PWR_DRAW) { appendLine("| Average Power Draw: $avrgPwrDraw") }
            flags.ifSet(EN_CONS) { appendLine("| Total Energy Consumed: $totEnCons") }
        }

    companion object Key : CoroutineContext.Key<NetSimEnRecorder> {
        val AVG_PWR_DRAW: Flag<NetSimEnRecorder> = Flag()
        val PWR_DRAW: Flag<NetSimEnRecorder> = Flag()
        val EN_CONS: Flag<NetSimEnRecorder> = Flag()
    }
}
