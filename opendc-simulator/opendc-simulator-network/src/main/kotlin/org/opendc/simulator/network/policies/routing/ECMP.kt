package org.opendc.simulator.network.policies.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow
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

        this@Node.routingTable.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .forEach {
                nodeFlowEntry.txPorts += it.associatedPort()
            }
    }
}
