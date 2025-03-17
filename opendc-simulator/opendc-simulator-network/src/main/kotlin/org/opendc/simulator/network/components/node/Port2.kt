package org.opendc.simulator.network.components.node

import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.link.ReceiveLink2
import org.opendc.simulator.network.components.link.SendLink2
import org.opendc.simulator.network.sync.invalidatable.Invalidatable

internal class Port2(owner: Node): Invalidatable by owner {
    private val sendLink: SendLink2? = null
    private val receiveLink: ReceiveLink2? = null

    suspend fun isActive(): Boolean {
        TODO()
    }

    suspend fun getUtilIn(): Percentage =
        receiveLink?.getUtil() ?: Percentage.zero

    suspend fun getUtilOut(): Percentage =
        sendLink?.getUtil() ?: Percentage.zero
}
