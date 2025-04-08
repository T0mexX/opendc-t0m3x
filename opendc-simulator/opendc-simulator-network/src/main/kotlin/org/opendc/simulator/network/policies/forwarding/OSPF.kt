package org.opendc.simulator.network.policies.forwarding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry

@Serializable
@SerialName("ospf")
internal data object OSPF: RoutingPolicy {
    context(Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        this@Node.routingTable.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .firstOrNull()
            ?.let { path ->
                nodeFlowEntry.txPorts.clear()
                nodeFlowEntry.txPorts += path.associatedPort()
            }
    }
}
