@file:OptIn(InternalOdcNetworkApi::class)

package org.opendc.simulator.network.utils.invalidatable

import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.InternalOdcNetworkApi

internal interface Invalidatable {
    val stabilizer: NetSimStabilizer

    suspend fun invalidate() {
        stabilizer.invalidate()
    }

    suspend fun validate() {
        stabilizer.validate()
    }
}
