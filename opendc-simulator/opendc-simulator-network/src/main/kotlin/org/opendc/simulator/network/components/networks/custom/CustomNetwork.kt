package org.opendc.simulator.network.components.networks.custom

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class CustomNetwork private constructor(
    nodes: Collection<Node<*>>,
    override val inet: Internet,
): NetworkImpl() {

    override val _nodesById: MutableMap<NodeId, Node<*>> = nodes.associateBy { it.id }.toMutableMap()
    override val _nodeLs: MutableList<Node<*>> = ArrayList(_nodesById.values)


    override val _sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    // TODO: do not crash when error for REPL
    context(NetSimScope)
    suspend operator fun plus(node: Node<*>) = barrier.whileStable {
        require(node.id != inet.id)
        require(node.id !in nodesById)

        _nodesById[node.id] = node
        (node as? SenderNode)?.let { _sendNodesById[it.id] = it }
        (node as? GlobalSwitch)?.msgSyncConnect(inet)
    }

    context(NetSimScope)
    suspend fun minus(nodeId: NodeId) = barrier.whileStable {
        require(nodeId in nodesById)

        val n: Node<*> = _nodesById.remove(nodeId)!!
        (n as? SenderNode)?.let { _sendNodesById -= it.id }
        n.job?.cancel()
    }

    /**
     * Connects [Node]s of ***this*** based on link-list passed as parameter.
     * If the link cannot be established, a warning message is logged and the link is ignored.
     * @param[links]    the list of pair representing the links to be established in the network.
     */
    context(NetSimScope)
    suspend fun connectFromLinkList(links: List<Pair<NodeId, NodeId>>) {
        fun warnOfUnsetLink(id1: NodeId, id2: NodeId, ) {
            this@NetSimScope.log.warn(
                "SimplexLink from (NodeId2=$id1) <-> (NodeId2=$id2) could not be established, " +
                    "one of the nodesById does not exist or it's connecting to itself.",
            )
        }

        links.forEach { (id1, id2) ->
            val node1: Node<*> =
                nodesById[id1] ?: let {
                    warnOfUnsetLink(id1, id2)
                    return@forEach
                }
            val node2: Node<*> =
                nodesById[id2] ?: let {
                    warnOfUnsetLink(id1, id2)
                    return@forEach
                }
            if (node1 === node2) {
                warnOfUnsetLink(id1, id2)
                return@forEach
            }
            node1.msgSyncConnect(node2)
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
                            .filterNot { it in doneNodes || it == INTERNET_ID }
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
        suspend operator fun invoke(nodes: Collection<Node<*>> = emptyList()): CustomNetwork {
            val inet = Internet()

            return CustomNetwork(
                nodes = nodes + inet,
                inet = inet,
            ).also { net ->
                net.getNodesById<GlobalSwitch>().values.forEach { cs -> cs.msgSyncConnect(net.inet) }

                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Setup global fairness policy if needed.
                this@NetSimScope.config.fairPolicy.setUp()

                // Register the network in the simulation scope.
                this@NetSimScope.registerNetwork(net)
            }
        }
    }
}
