package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 *
 * Approximation, load based routing is recomputed only when new flow arrives at node,
 * not for each single packet.
 *
 * The tx [Port] is selected considering both the path length and the congestion at the port.
 */
@Serializable
@SerialName("ugal-l")
internal class UGALL : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = this

    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        nodeFlowEntry.txPorts.clear()

        // Ports the flow will be forwarded to.
        val paths = this@Node.routingTable.getPossiblePathsTo(f.destId).onlyMinimal()

        // Choose path considering both path length and congestion at the port.
        val chosenPath = paths.minBy { path ->
            // Port to send to if this path is used.
            val port = path.associatedPort()

            // Score for the port, less is better.
            path.numOfHops * port.txLink!!.util.value
        }

        // Set the chosen port as the only one that will handle the outgoing flow.
        nodeFlowEntry.txPorts[chosenPath.associatedPort()] = Percentage.ofPercentage(100)
    }
}
