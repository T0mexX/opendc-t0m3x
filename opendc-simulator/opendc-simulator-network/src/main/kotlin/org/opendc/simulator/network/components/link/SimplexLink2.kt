package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage

internal class SimplexLink2(
    val maxBw: DataRate
) {
    var usedBW: DataRate = DataRate.zero
        private set

    val util: Percentage get() = usedBW / maxBw



    val m = Channel<Unit>()

    fun bo() {
        m.onSend
    }

    fun claimBw(bw: DataRate): DataRate = TODO()




}
