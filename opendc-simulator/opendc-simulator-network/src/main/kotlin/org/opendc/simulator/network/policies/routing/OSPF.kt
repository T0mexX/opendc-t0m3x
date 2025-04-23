package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.simscope.NetSimScope


@Serializable
@SerialName("ospf")
internal class OSPF: RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = ECMP()

    context(NetSimScope, Node<*>) override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        assert(nodeFlowEntry.txPorts.isEmpty())

        this@Node.routingTable.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .firstOrNull()
            ?.let { path ->
                nodeFlowEntry.txPorts.clear()
                nodeFlowEntry.txPorts += path.associatedPort()
            }
    }

    companion object {
        context(NetSimScope, Node<*>)
        suspend fun selectPorts(to: Node<*>): Set<Port> =
            setOf(
                this@Node.routingTable.getPossiblePathsTo(to.id)
                    .onlyMinimal()
                    .random()
                    .associatedPort()
            )
    }
}

///**
// * TODO
// */
//@Serializable
//@SerialName("ospf")
//internal data object OSPF: RoutPolicy {
//    context(NetSimScope, Node<*>)
//    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
//        val f = nodeFlowEntry.netFlow
//        this@Node.routingTable.getPossiblePathsTo(f.destId)
//            .onlyMinimal()
//            .firstOrNull()
//            ?.let { path ->
//                nodeFlowEntry.txPorts.clear()
//                nodeFlowEntry.txPorts += path.associatedPort()
//            }
//    }
//
//    context(NetSimScope, Node<*>)
//    suspend fun selectPorts(f: NetFlow): Set<Port> =
//        setOf(
//            this@Node.routingTable.getPossiblePathsTo(f.destId)
//                .onlyMinimal()
//                .random()
//                .associatedPort()
//        )
//
//    context(NetSimScope, Node<*>)
//    suspend fun selectPorts(to: Node<*>): Set<Port> =
//        setOf(
//            this@Node.routingTable.getPossiblePathsTo(to.id)
//                .onlyMinimal()
//                .random()
//                .associatedPort()
//        )
//}
