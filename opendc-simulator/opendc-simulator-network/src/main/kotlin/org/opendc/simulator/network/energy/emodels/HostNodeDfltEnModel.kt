package org.opendc.simulator.network.energy.emodels

import org.opendc.common.units.DataRate
import org.opendc.common.units.Power
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.energy.EnModel
import kotlin.math.pow


/**
 * Model based on [P. Reviriego, K. Christensen, J. Rabanillo and J. A. Maestro, "An Initial Evaluation of Energy Efficient Ethernet,"](https://ieeexplore.ieee.org/abstract/document/5743052).
 * In particular using Energy Efficient Ethernet (EEE). The energy consumption is defined for 100Mbps BASE-TX and 1000Mbps BASE-T NICs,
 * other data rate dynamic and static pwr draw are gathered from a power regression.
 */
internal object HostNodeDfltEnModel: EnModel<HostNode> {
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
        Power.ofWatts(  116.24 * maxPortSpeed.toMbps().pow(0.0388288) / 1e3  )

    /**
     * Obtained through power model regression on the 2 defined consumptions.
     */
    private fun activePwrFromCurrRate(currPortSpeed: DataRate): Power =
        Power.ofWatts(  2.23949 * currPortSpeed.toMbps().pow(0.74435) / 1e3  )

    override fun computeCurrConsumpt(e: HostNode): Power {
        val activePorts: Collection<Port> = e.getActivePorts()
        val idlePwr: Power = activePorts.sumOfUnit { port ->
            passivePwrFromMaxPortSpeed(port.currSpeed)
        }
        val activePwr: Power = activePorts.sumOfUnit { port ->
            val currPortRate: DataRate = port.currSpeed * port.util
            activePwrFromCurrRate(currPortRate)
        }

        return idlePwr + activePwr
    }

    /**
     *  @return the ports of ***this*** [Switch] that are currently active.
     *  @see[Port.isActive]
     */
    private fun HostNode.getActivePorts(): Collection<Port> = this.portToNode.values.filter { it.isActive }
}
