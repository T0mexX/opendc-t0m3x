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

package org.opendc.simulator.network.api

import org.opendc.common.units.Energy
import org.opendc.common.units.Power
import org.opendc.common.units.Time
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.stability.NetworkStabilityChecker.Key.getNetStabilityChecker
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.logger
import kotlin.coroutines.coroutineContext

/**
 * Records the network's power draw and energy consumption.
 */
public class NetEnRecorder internal constructor(network: Network) {
    public var currPwrUsage: Power = Power.ZERO
        private set

    public var avrgPwrUsage: Power = Power.ZERO
        private set
    private var totTimeElapsed: Time = Time.ZERO

    public var totalConsumption: Energy = Energy.ZERO
        private set

    private val consumersById: MutableMap<NodeId, EnergyConsumer<*>> =
        network.nodesById.values.filterIsInstance<EnergyConsumer<*>>().associateBy { it.id }.toMutableMap()

    private val powerUseOnChangeHandler =
        OnChangeHandler<EnMonitor<*>, Power> { _, oldValue, newValue ->
            currPwrUsage += newValue - oldValue
        }

    init {
        // Sets up listeners on the energy consuming nodes.
        consumersById.values.forEach { it.enMonitor.onPwrUseChange(powerUseOnChangeHandler) }
        consumersById.values.forEach { it.enMonitor.update() }

        // Sets up callback for whenever a node is added to the network.
        (network as? CustomNetwork)?.onNodeAdded { _, node ->
            (node as? EnergyConsumer<*>)?.let { newConsumer ->
                newConsumer.enMonitor.onPwrUseChange(powerUseOnChangeHandler)
                consumersById.compute(newConsumer.id) { _, oldConsumer ->
                    // If new consumer replaces an old one log warning msg
                    oldConsumer?.let {
                        if (oldConsumer !== newConsumer) {
                            log.warn("energy consumer $oldConsumer is being replaced by $newConsumer which has the same id")
                        }
                    }
                    newConsumer.also {
                        it.enMonitor.onPwrUseChange(powerUseOnChangeHandler)
                        it.enMonitor.update()
                    }
                }
            }
        }

        // Sets up a callback for whenever a node is removed from the network.
        (network as? CustomNetwork)?.onNodeRemoved { _, node ->
            (node as? EnergyConsumer<*>)?.let {
                consumersById.remove(it.id)
                    ?: log.warn("energy consumer was removed from the network $network, but it was not tracked by the energy recorder")
            }
        }
    }

    /**
     * @return a formatted [String] that displays energy consumption and power draw of the network.
     * Preferably to be logged on a new line.
     */
    internal fun fmt(flags: Flags<NetEnRecorder> = Flags.all()): String =
        buildString {
            appendLine("| ==== Energy Report ====")
            flags.ifSet(PWR_DRAW) { appendLine("| Current Power Draw: $currPwrUsage") }
            flags.ifSet(AVG_PWR_DRAW) { appendLine("| Average Power Draw: $avrgPwrUsage") }
            flags.ifSet(EN_CONS) { appendLine("| Total Energy Consumed: $totalConsumption") }
        }

    /**
     * Advances the time by [Time], updating energy consumption and average power draw accordingly.
     */
    internal suspend fun advanceBy(deltaTime: Time) {
        coroutineContext.getNetStabilityChecker().checkIsStableWhile {
            // Update total energy consumption.
            totalConsumption += currPwrUsage * deltaTime

            // Advance time of each node's energy monitor.
            consumersById.values.forEach { it.enMonitor.advanceBy(deltaTime) }

            // Update average power usage.
            avrgPwrUsage = (
                (avrgPwrUsage * totTimeElapsed.toSec()) +
                    currPwrUsage * deltaTime.toSec()
            ) / (totTimeElapsed + deltaTime).toSec()
            totTimeElapsed += deltaTime
        }
    }

    public companion object {
        private val log by logger()

        public val AVG_PWR_DRAW: Flag<NetEnRecorder> = Flag()
        public val PWR_DRAW: Flag<NetEnRecorder> = Flag()
        public val EN_CONS: Flag<NetEnRecorder> = Flag()
    }
}
