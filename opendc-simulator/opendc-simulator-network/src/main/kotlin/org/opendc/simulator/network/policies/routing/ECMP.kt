package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope


/**
 * TODO
 */
@Serializable
@SerialName("ecmp")
internal class ECMP : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = this

    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        nodeFlowEntry.txPorts.clear()

        // Ports the flow will be forwarded to.
        val txPorts = this@Node.routTbl.getPossiblePathsTo(f.destId).onlyMinimal()

        // Add the tx ports to the `NodeFlowEntry`, with the corresponding
        // percentage of this flow's data forwarded to those ports.
        // Each port is assigned an equal share of the total data to send
        txPorts.forEach {
            nodeFlowEntry.txPorts[it.associatedPort()] = Percentage.ofRatio(1.0 / txPorts.size)
        }
    }
}
