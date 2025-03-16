package org.opendc.simulator.network.components.link

import org.opendc.common.units.DataRate

internal data class FlowRateChange(var from: DataRate, var to: DataRate)
