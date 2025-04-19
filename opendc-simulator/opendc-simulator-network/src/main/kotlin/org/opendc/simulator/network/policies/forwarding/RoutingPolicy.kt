package org.opendc.simulator.network.policies.forwarding

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.flow.publics.NetFlow

/**
 * TODO
 */
@Serializable
internal sealed interface RoutingPolicy {
    context(Node<*>)
    suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry)

    /**
     * Filters ***this*** collection of [RoutingTable.PossiblePath], keeping only those that are minimal.
     */
    fun Collection<RoutingTable.PossiblePath>.onlyMinimal(): Collection<RoutingTable.PossiblePath> {
        val min: Int = this.minOfOrNull { it.numOfHops } ?: 0
        return this.filter { it.numOfHops == min }
    }
}
