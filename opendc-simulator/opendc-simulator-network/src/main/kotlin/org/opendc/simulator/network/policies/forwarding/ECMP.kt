package org.opendc.simulator.network.policies.forwarding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry


@Serializable
@SerialName("ecmp")
internal data object ECMP : RoutingPolicy {
    context(Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        nodeFlowEntry.txPorts.clear()

        this@Node.routingTable.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .forEach {
                nodeFlowEntry.txPorts += it.associatedPort()
            }
    }
}
