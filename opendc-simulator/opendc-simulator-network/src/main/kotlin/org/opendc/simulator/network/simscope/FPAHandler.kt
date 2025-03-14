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

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.Energy
import org.opendc.common.units.Power
import org.opendc.common.units.TimeDelta

/**
 * Configuration for handling floating-point inaccuracies during simulation.
 *
 * This class defines optional rounding/clamping thresholds for various physical quantities
 * (e.g., data rate, power, energy, time) to account for minor floating-point errors.
 *
 * For each quantity, two types of thresholds can be specified:
 *
 * - `roundOverX`: The maximum allowable deviation beyond a bound. If the deviation exceeds
 *   this threshold, an exception is thrown.
 * - `roundNearX`: The threshold under which a value close to a bound will be clamped to that bound.
 *
 * e.g.:
 *     If we have a data rate that we know it should be between `0bps` and `1Gbps`,
 *     and `roundOverDR = 10Kbps`,
 *     and `roundNearDR = 1Kbps`,
 *     then if after computation we get:
 *         - (-inf, 0bps - 10Kbps = -10Kbps) -> error
 *         - [-10Kbps, 1Kbps) -> round to 0bps
 *         - [1Kbps, 1Gbps - 1Kbps = 999,999Kbps] -> keep value
 *         - (999,999Kbps, 1Gbps + 10Kbps = 100,010Kbps] -> round to 1Gbps
 *         - (100,010Kbps, +inf) -> error
 *
 * - This is usefully to manage the balance between correcting errors that
 *                 may have been introduced by fpa, and detecting simulation/implementation errors.
 */
@Serializable
internal class FPAHandler(
    val roundOverDR: DataRate? = null,
    val roundNearDR: DataRate = DataRate.ofbps(0.00001),
    val roundOverTS: TimeDelta? = null,
    val roundNearTS: TimeDelta? = null,
    val roundOverTD: TimeDelta? = null,
    val roundNearTD: TimeDelta? = null,
    val roundOverPwr: Power? = null,
    val roundNearPwr: Power? = null,
    val roundOverEn: Energy? = null,
    val roundNearEn: Energy? = null,
    val roundOverDS: DataSize? = null,
    val roundNearDS: DataSize? = null,
) {
    companion object {
        /**
         * Applies boundary correction to this [DataRate] based on configured floating-point tolerances.
         *
         * - If the value is within [min]–[max] but closer to a bound than [roundNearDR], it is clamped to that bound.
         * - If the value is slightly outside the [min]–[max] range but within [roundOverDR]
         * (or if [roundOverDR] is `null`), it is clamped to the nearest bound.
         * - If the value exceeds [min] or [max] by more than [roundOverDR], an [IllegalStateException] is thrown.
         * - Otherwise, the value is returned unchanged.
         *
         * @throws IllegalStateException If the value exceeds the specified bounds beyond the allowed error margin.
         */
        context(NetSimScope)
        fun DataRate.roundDR(
            min: DataRate? = null,
            max: DataRate? = null,
        ): DataRate {
            assert(min == null || max == null || min <= max)
            val value = this@DataRate
            val roundOverDR = this@NetSimScope.devConfig.fpaHandler.roundOverDR
            val roundNearDR = this@NetSimScope.devConfig.fpaHandler.roundNearDR

            min?.let {
                val deltaLow = value - min
                if (roundOverDR != null && -deltaLow > roundOverDR) throwErr("data rate")
                if (deltaLow < roundNearDR) return min
            }

            max?.let {
                val deltaHigh = value - max
                if (roundOverDR != null && deltaHigh > roundOverDR) throwErr("data rate")
                if (-deltaHigh < roundNearDR) return max
            }

            return value
        }

        private fun throwErr(unit: String): Nothing =
            throw IllegalStateException("Floating point arithmetic error for $unit accumulated over the allowed error")
    }
}
