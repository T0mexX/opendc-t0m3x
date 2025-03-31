package org.opendc.simulator.network.utils.invalidatable.internals

import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal interface IInvalidatable : Invalidatable {
    val stabilizer: NetSimStabilizer

    override suspend fun invalidate() {
        stabilizer.invalidate()
    }

    override suspend fun validate() {
        stabilizer.validate()
    }
}
