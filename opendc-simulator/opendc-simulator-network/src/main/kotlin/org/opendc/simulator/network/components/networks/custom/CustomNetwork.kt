/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.components.networks.custom

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.node.switchh.SwitchSpecs
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.components.node.terminal.TerminalSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class CustomNetwork private constructor(
    nodes: Collection<Node<*>>,
    override val inet: Internet,
) : NetworkImpl<CustomNetwork>() {
    override val nodesById: MutableMap<NodeId, Node<*>> = nodes.associateBy { it.id }.toMutableMap()

    override val sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        getNodesById<SenderNode<*>>().toMutableMap()

    /**
     * Recomputed on each call.
     */
    override val specs: CustomNetworkSpecs get() = toSpecs()

    // TODO: do not crash when error for REPL
    context(NetSimScope)
    suspend operator fun plus(n: Node<*>) {
        assert(netCoId.owner == NetCo.MAIN)
        barrier.whileStable {
            require(n.id != inet.id)
            require(n.id !in nodesById)

            nodesById[n.id] = n
            (n as? SenderNode)?.let { sendNodesById[it.id] = it }
            if (n is Switch && n.global) n.msgSyncConnect(inet)
            this@NetSimScope.routPolicy.onNodeAdded(n)
        }
    }

    context(NetSimScope)
    suspend fun minus(nodeId: NodeId) {
        assert(netCoId.owner == NetCo.MAIN)
        barrier.whileStable {
            require(nodeId in nodesById)

            val n: Node<*> = nodesById.remove(nodeId)!!
            (n as? SenderNode)?.let { sendNodesById -= it.id }
            n.netCancel()
            this@NetSimScope.routPolicy.onNodeRemoved(n)
        }
    }

    /**
     * Connects [Node]s of ***this*** based on link-list passed as parameter.
     * If the link cannot be established, a warning message is logged and the link is ignored.
     * @param[links]    the list of pair representing the links to be established in the network.
     */
    context(NetSimScope)
    suspend fun connectFromLinkList(links: List<Pair<NodeId, NodeId>>) {
        fun warnOfUnsetLink(
            id1: NodeId,
            id2: NodeId,
        ) {
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

    fun toSpecs(): CustomNetworkSpecs {
        val links: List<Pair<NodeId, NodeId>> =
            buildList {
                val nodes = nodesById.values.filterNot { it is Internet }
                val doneNodes = mutableSetOf<NodeId>()

                nodes.forEach { node ->
                    addAll(
                        node.links.mapNotNull { it?.receiverN?.id }
                            .filterNot { it in doneNodes || it == INTERNET_ID }
                            .map { Pair(it, node.id) },
                    )
                    doneNodes.add(node.id)
                }
            }

        @Suppress("UNCHECKED_CAST")
        return CustomNetworkSpecs(
            terminalSpecs = getNodesById<Terminal>().values.map { it.toSpecs() } as List<TerminalSpecs>,
            switchSpecs = getNodesById<Switch>().values.map { it.toSpecs() } as List<SwitchSpecs>,
            links = links,
        )
    }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(specs: CustomNetworkSpecs): CustomNetwork {
            val inet = Internet()
            val nodes: List<Node<*>> = (specs.terminalSpecs + specs.switchSpecs).map { it.build(inet = inet) }
            val distinctNodes = nodes.distinctBy { it.id }
            if (nodes.size != distinctNodes.size) {
                log.warn("Some nodesById with already existing ids got filtered out.")
            }
            return CustomNetwork(
                nodes = nodes + inet,
                inet = inet,
            ).also { net ->
                net.connectFromLinkList(specs.links)

                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Register the network in the simulation rootScope.
                this@NetSimScope.registerNetwork(net)
            }
        }

        context(NetSimScope)
        suspend operator fun invoke(): CustomNetwork {
            val inet = Internet()
            return CustomNetwork(listOf(inet), inet).also { net ->
                // Setup global routing policy if needed.
                this@NetSimScope.config.routPolicy.setUp()

                // Register the network in the simulation rootScope.
                this@NetSimScope.registerNetwork(net)
            }
        }
    }
}
