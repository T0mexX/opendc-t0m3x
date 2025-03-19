package org.opendc.simulator.network.components.link

import org.opendc.common.units.Percentage

internal fun interface ReceiveLink {
    suspend fun getUtil(): Percentage
}
