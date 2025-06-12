@file:OptIn(InternalODCNetworkApi::class)

package org.opendc.simulator.network.utils.invalidatable.internals

import org.opendc.simulator.network.utils.InternalODCNetworkApi

public interface Invalidatable {
    public suspend fun invalidate()

    public suspend fun validate()
}
