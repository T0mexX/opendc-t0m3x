@file:OptIn(InternalOdcNetworkApi::class)

package org.opendc.simulator.network.utils.invalidatable.internals

import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.InternalOdcNetworkApi

public interface Invalidatable {
    public suspend fun invalidate()

    public suspend fun validate()
}
