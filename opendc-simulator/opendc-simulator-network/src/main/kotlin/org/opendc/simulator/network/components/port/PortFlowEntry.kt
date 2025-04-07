package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.NetFlow

internal class PortFlowEntry(
    var used: Boolean = false,
    var demand: DataRate = DataRate.zero,
    var tput: DataRate = DataRate.zero,
) {
    lateinit var netF: INetFlow
}
