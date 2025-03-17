package org.opendc.simulator.network.components.link

import org.opendc.common.units.Percentage

internal interface ReceiveLink2 {
    suspend fun receive(): DeltaFlow
    suspend fun getUtil(): Percentage
}
