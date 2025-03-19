package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.publics.FlowId2
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.utils.tracker.Trackable
import org.opendc.simulator.network.utils.tracker.Tracker

internal class PortFlowEntry(
    var used: Boolean = false,
    var txDemand: DataRate = DataRate.zero,
    var txThroughput: DataRate = DataRate.zero,
) {
    lateinit var netFlow: NetFlow
}
