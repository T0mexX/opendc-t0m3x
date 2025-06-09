package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope


@Serializable
@SerialName("min")
internal class MIN: RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = ECMP()

    context(NetSimScope, Node<*>) override suspend fun selectPorts(nodeFEntry: NodeFlowEntry) {
        val f = nodeFEntry.netFlow
        assert(nodeFEntry.txlinks.isEmpty())

        this@Node.routTbl.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .takeIf { it.isNotEmpty() }
            ?.first()
            ?.let { path ->
                nodeFEntry.txlinks.clear()
                nodeFEntry.txlinks[path.associatedLink()] = Percentage.ofPercentage(100)
            }
    }

    companion object {
        context(NetSimScope, Node<*>)
        suspend fun selectPort(to: NodeId): Link =
            this@Node.routTbl.getPossiblePathsTo(to)
                .onlyMinimal()
                .first()
                .associatedLink()
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
//        this@Node.routTbl.getPossiblePathsTo(f.destId)
//            .onlyMinimal()
//            .firstOrNull()
//            ?.let { path ->
//                nodeFlowEntry.txlinks.clear()
//                nodeFlowEntry.txlinks += path.associatedPort()
//            }
//    }
//
//    context(NetSimScope, Node<*>)
//    suspend fun selectPorts(f: NetFlow): Set<Port> =
//        setOf(
//            this@Node.routTbl.getPossiblePathsTo(f.destId)
//                .onlyMinimal()
//                .random()
//                .associatedPort()
//        )
//
//    context(NetSimScope, Node<*>)
//    suspend fun selectPorts(to: Node<*>): Set<Port> =
//        setOf(
//            this@Node.routTbl.getPossiblePathsTo(to.id)
//                .onlyMinimal()
//                .random()
//                .associatedPort()
//        )
//}
