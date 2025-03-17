package org.opendc.simulator.network.components.link

import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage

internal interface SendLink2 {
    suspend fun send(deltaFlow: DeltaFlow)
    val maxBw: DataRate
    suspend fun getUtil(): Percentage
    suspend fun claimBw(bw: DataRate): DataRate
    suspend fun releaseBw(bw: DataRate)
}
