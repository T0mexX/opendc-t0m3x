package org.opendc.simulator.network.components.link

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.sync.invalidatable.Invalidatable

internal class SimplexLink2 private constructor(
    to: Invalidatable,
    override val maxBw: DataRate,
): org.opendc.simulator.network.utils.invalidatable.InvalidatorChl<DeltaFlow>(receiver = to), SendLink2, ReceiveLink2 {
    private var usedBw: DataRate = DataRate.zero
    private val mtx = Mutex()

    override suspend fun getUtil(): Percentage = mtx.withLock {
        usedBw / maxBw
    }

    override suspend fun claimBw(bw: DataRate): DataRate = mtx.withLock {
        val available: DataRate = maxBw - usedBw
        return if (bw > available) {
            usedBw = maxBw
            available
        } else {
            usedBw += bw
            bw
        }
    }

    override suspend fun releaseBw(bw: DataRate) = mtx.withLock {
        require(bw < usedBw)
        usedBw -= bw
    }
}
