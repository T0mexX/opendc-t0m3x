package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("olpf")
internal data object OLPF: RoutPolicy() {
    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        this@Node.routingTable.getPossiblePathsTo(f.destId)
            .onlyMaximal()
            .firstOrNull()
            ?.let { path ->
                nodeFlowEntry.txPorts.clear()
                nodeFlowEntry.txPorts += path.associatedPort()
            }
    }

    private fun Collection<RoutingTable.PossiblePath>.onlyMaximal(): Collection<RoutingTable.PossiblePath> {
        val max = this.minOfOrNull { it.numOfHops }
        return this.filter { it.numOfHops == max }
    }
}
