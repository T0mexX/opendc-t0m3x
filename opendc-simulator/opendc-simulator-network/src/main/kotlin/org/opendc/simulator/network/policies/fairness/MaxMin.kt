package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.port.PortFlowEntry

/**
 * TODO
 */
@Serializable
@SerialName("maxmin")
internal class MaxMin : FairnessPolicy() {
    context(Link)
    override suspend fun applyFairness(entryList: List<PortFlowEntry>) {
        TODO("Not yet implemented")
    }
}
