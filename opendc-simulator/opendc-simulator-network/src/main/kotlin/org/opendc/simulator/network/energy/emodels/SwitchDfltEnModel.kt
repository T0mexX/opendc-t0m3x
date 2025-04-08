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

package org.opendc.simulator.network.energy.emodels

import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.common.units.Power
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.energy.EnModel
import kotlin.math.log
import kotlin.math.pow

/**
 * Model defined by Xiaodong Wang et al. in 'CARPO:
 * Correlation-Aware Power Optimization in Data Center Networks'
 *
 * Only derived by 1000Mbps 48 ports PRONTO 3240 switches.
 */
internal data object SwitchDfltEnModel : EnModel<Switch> {
    private val CHASSIS_PWR: Power = Power.ofWatts(67.7)
    private val IDLE_PORT_PWR_10Mbps: Power = Power.ofWatts(3.0 / 48)
    private val IDLE_PORT_PWR_100Mbps: Power = Power.ofWatts(12.5 / 48)
    private val IDLE_PORT_PWR_1000Mbps: Power = Power.ofWatts(43.8 / 48)

    /**
     * The dynamic power usage of the port is equal to its utilization * 5% * static power consumption.
     */
    private const val STATIC_TO_DYNAMIC_RATIO: Double = 0.05

    override fun computeCurrConsumpt(e: Switch): Power {
        val idlePortPwr: Power = getPortIdlePwr(e.portSpeed)
        // TODO: change to use port current speed, that in the future could be different from its max speed.
        //      The switch can lower its port speed to save energy.
        val numOfActivePorts: Int = e.getActivePorts().size

        val totalStaticPwr: Power = CHASSIS_PWR + idlePortPwr * numOfActivePorts
        val totalDynamicPwr: Power = totalStaticPwr * STATIC_TO_DYNAMIC_RATIO * e.avrgPortUtilization().toRatio()

        return totalStaticPwr + totalDynamicPwr
    }

    /**
     * Returns static power consumption of a single active port of speed [portSpeed].
     * @param[portSpeed]    speed of the port to compute static energy consumption of.
     */
    private fun getPortIdlePwr(portSpeed: DataRate): Power {
        return when (portSpeed.toMbps()) {
            10.0 -> IDLE_PORT_PWR_10Mbps
            100.0 -> IDLE_PORT_PWR_100Mbps
            1000.0 -> IDLE_PORT_PWR_1000Mbps
            else -> {
                /**
                 * Just approximated poorly from the 3 constants.
                 * TODO: change
                 */
                Power.ofWatts(4.0.pow(log(portSpeed.toMbps(), 10.0) - 1) * (3.0 / 48.0))
            }
        }
    }

    /**
     *  @return the ports of ***this*** [Switch] that are currently active.
     *  @see[Port.isActive]
     */
    private fun Switch.getActivePorts(): Collection<Port> =
        ports.filter { (it.txLink?.util ?: Percentage.zero) > Percentage.zero }

    /**
     * @return average port utilization considering both active and not active ports.
     */
    private fun Switch.avrgPortUtilization(): Percentage =
        this.ports.sumOfUnit { it.txLink?.util ?: Percentage.zero } / ports.size
}
