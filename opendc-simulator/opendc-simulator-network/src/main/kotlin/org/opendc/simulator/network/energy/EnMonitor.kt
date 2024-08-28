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

package org.opendc.simulator.network.energy

import org.opendc.common.units.Energy
import org.opendc.common.units.Power
import org.opendc.common.units.Time
import org.opendc.simulator.network.components.stability.NetworkStabilityChecker.Key.getNetStabilityChecker
import org.opendc.simulator.network.utils.OnChangeHandler
import kotlin.coroutines.coroutineContext
import kotlin.properties.Delegates

/**
 * Monitors the energy consumption of [monitored] of type `T`.
 * @param[monitored]    network component monitored by ***this***.
 * @param[enModel]      energy model to use to compute current energy consumption for [monitored] network component.
 */
internal class EnMonitor<T : EnergyConsumer<T>>(
    private val monitored: T,
    private val enModel: EnModel<T> = monitored.getDfltEnModel(),
) {
    /**
     * Callback functions of the observers of the [currPwrUsage] field.
     */
    private val obs: MutableList<OnChangeHandler<EnMonitor<T>, Power>> = mutableListOf()

    /**
     * The current energy consumption of the [monitored] network component.
     */
    var currPwrUsage: Power by Delegates.observable(Power.ZERO) { _, oldValue, newValue ->
        obs.forEach { it.handleChange(this, oldValue, newValue) }
    }
        private set

    var avrgPwrUsage: Power = Power.ZERO
        private set
    private var totTimeElapsed: Time = Time.ZERO

    var totEnConsumpt: Energy = Energy.ZERO
        private set

    /**
     * Adds an observer callback function.
     * @param[f]    callback function of the new observer.
     */
    fun onPwrUseChange(f: OnChangeHandler<EnMonitor<T>, Power>) {
        obs.add(f)
    }

    /**
     * Updates the energy consumption of [monitored].
     *
     * Ideally called by [monitored] whenever its state changes.
     */
    fun update() {
        currPwrUsage = enModel.computeCurrConsumpt(monitored)
    }

    suspend fun advanceBy(deltaTime: Time) =
        coroutineContext.getNetStabilityChecker().checkIsStableWhile {
            // Update total energy consumption
            totEnConsumpt += currPwrUsage * deltaTime

            // Update average power usage.
            avrgPwrUsage = (
                (avrgPwrUsage * totTimeElapsed.toSec()) +
                    currPwrUsage * deltaTime.toSec()
            ) / (totTimeElapsed + deltaTime).toSec()
            totTimeElapsed += deltaTime
        }
}
