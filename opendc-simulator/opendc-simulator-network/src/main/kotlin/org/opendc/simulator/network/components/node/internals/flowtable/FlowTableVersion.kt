package org.opendc.simulator.network.components.node.internals.flowtable

import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.simscope.NetSimScope

internal fun interface FlowTableVersion {
    context(NetSimScope)
    suspend operator fun invoke(): FlowTable
}
