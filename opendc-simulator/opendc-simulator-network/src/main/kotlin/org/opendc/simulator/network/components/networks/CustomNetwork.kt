package org.opendc.simulator.network.components.networks

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.specs.CustomNetworkSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class CustomNetwork(
    nodes: Collection<Node>,
    override val internet: Internet,
): NetworkV0() {

    override val _nodesById: MutableMap<NodeId, Node> = nodes.associateBy { it.id }.toMutableMap()
    override val _sendNodesById: MutableMap<NodeId, SenderNode> =
        getNodesById<SenderNode>().toMutableMap()

    // TODO: do not crash when error for REPL
    context(NetSimScope)
    operator fun plus(node: Node) {
        require(node.id == internet.id)
        require(node.id !in nodesById)

        _nodesById[node.id] = node
        (node as? SenderNode)?.let { _sendNodesById[it.id] = it }
        node.netLaunch()
    }

    context(NetSimScope)
    suspend fun minus(nodeId: NodeId) {
        require(nodeId in nodesById)

        val n: Node = _nodesById.remove(nodeId)!!
        (n as? SenderNode)?.let { _sendNodesById -= it.id }
        n.job.cancel()
    }

    /**
     * Connects [Node]s of ***this*** based on link-list passed as parameter.
     * If the link cannot be established, a warning message is logged and the link is ignored.
     * @param[links]    the list of pair representing the links to be established in the network.
     */
    context(NetSimScope)
    suspend fun connectFromLinkList(links: List<Pair<NodeId, NodeId>>) {
        fun warnOfUnsetLink(id1: NodeId, id2: NodeId, ) {
            this@NetSimScope.logger.warn(
                "SimplexLink from (NodeId2=$id1) <-> (NodeId2=$id2) could not be established, " +
                    "one of the nodesById does not exist or it's connecting to itself.",
            )
        }

        links.forEach { (id1, id2) ->
            val node1: Node =
                nodesById[id1] ?: let {
                    warnOfUnsetLink(id1, id2)
                    return@forEach
                }
            val node2: Node =
                nodesById[id2] ?: let {
                    warnOfUnsetLink(id1, id2)
                    return@forEach
                }
            if (node1 === node2) {
                warnOfUnsetLink(id1, id2)
                return@forEach
            }
            node1.connectTo(node2)
        }
    }

    override fun toSpecs(): Specs<CustomNetwork> {
        val links: List<Pair<NodeId, NodeId>> =
            buildList {
                val nodes = nodesById.values.filterNot { it is Internet }
                val doneNodes = mutableSetOf<NodeId>()

                nodes.forEach { node ->
                    addAll(
                        node.ports.mapNotNull { it.txLink?.receiverPort?.owner?.id }
                            .filterNot { it in doneNodes }
                            .map { Pair(it, node.id) },
                    )
                    doneNodes.add(node.id)
                }
            }

        return CustomNetworkSpecs(
            nodesSpecs = nodesById.values.filterNot { it is Internet }.map { it.toSpecs() },
            links = links,
        )
    }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(nodes: Collection<Node>): CustomNetwork =
            CustomNetwork(
                nodes = nodes,
                internet = Internet(),
            )
    }
}
