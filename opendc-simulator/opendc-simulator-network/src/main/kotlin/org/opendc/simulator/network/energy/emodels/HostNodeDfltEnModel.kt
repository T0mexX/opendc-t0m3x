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

package org.opendc.simulator.network.energy.emodels

import org.opendc.common.units.DataRate
import org.opendc.common.units.Power
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.energy.EnModel
import kotlin.math.pow

/**
 * Model based on [P. Reviriego, K. Christensen, J. Rabanillo and J. A. Maestro, "An Initial Evaluation of Energy Efficient Ethernet,"](https://ieeexplore.ieee.org/abstract/document/5743052).
 * In particular using Energy Efficient Ethernet (EEE). The energy consumption is defined for 100Mbps BASE-TX and 1000Mbps BASE-T NICs,
 * other data rate dynamic and static pwr draw are gathered from a power regression.
 */
internal object HostNodeDfltEnModel : EnModel<Terminal> {
    private val IDLE_PWR_100Mbps: Power = Power.ofWatts(139 / 1e3)
    private val IDLE_PWR_1000Mbps: Power = Power.ofWatts(152 / 1e3)
    private val FULL_LOAD_PWR_100Mbps: Power = Power.ofWatts(208 / 1e3)
    private val FULL_LOAD_PWR_1000Mbps: Power = Power.ofWatts(535 / 1e3)
    private val FULL_LOAD_ACTIVE_PWR_100Mbps: Power =
        FULL_LOAD_PWR_100Mbps - IDLE_PWR_100Mbps
    private val FULL_LOAD_ACTIVE_PWR_1000Mbps: Power =
        FULL_LOAD_PWR_1000Mbps - IDLE_PWR_1000Mbps

    /**
     * Obtained through power model regression on the 2 defined consumptions.
     */
    private fun passivePwrFromMaxPortSpeed(maxPortSpeed: DataRate): Power =
        Power.ofWatts(116.24 * maxPortSpeed.toMbps().pow(0.0388288) / 1e3)

    /**
     * Obtained through power model regression on the 2 defined consumptions.
     */
    private fun activePwrFromCurrRate(currPortSpeed: DataRate): Power = Power.ofWatts(2.23949 * currPortSpeed.toMbps().pow(0.74435) / 1e3)

    override fun computeCurrConsumpt(e: Terminal): Power {
        val activeLinks: Collection<Link> = e.getActiveLinks()
        val idlePwr: Power =
            activeLinks.sumOfUnit { l ->
                passivePwrFromMaxPortSpeed(l.maxBw)
            }
//        check(idlePwr > Power.ZERO) {"${idlePwr.toWatts()}, "}
        val activePwr: Power =
            activeLinks.sumOfUnit { l ->
                val currPortRate: DataRate = l.maxBw * l.util.toRatio()
                activePwrFromCurrRate(currPortRate)
            }

        return idlePwr + activePwr
    }

    private fun Terminal.getActiveLinks(): Collection<Link> = this.links.filterNotNull()
}
