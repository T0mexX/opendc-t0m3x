package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.internalstructs.RoutTbl
import org.opendc.simulator.network.components.internalstructs.RoutTbl2
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
@Serializable
@SerialName("olpf")
internal data object OLPF: RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = ECMP()

    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        this@Node.routTbl.getPossiblePathsTo(f.destId)
            .onlyMaximal()
            .firstOrNull()
            ?.let { path ->
                nodeFlowEntry.txPorts.clear()
                nodeFlowEntry.txPorts[path.associatedPort()] = Percentage.ofPercentage(100)
            }
    }

    private fun Collection<RoutTbl2.RoutTblPath>.onlyMaximal(): Collection<RoutTbl2.RoutTblPath> {
        val max = this.maxOfOrNull { it.distance }
        return this.filter { it.distance == max }
    }
}
