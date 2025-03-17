/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.components

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.serializer
import org.opendc.common.logger.errAndNull
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.NetFlow
import org.opendc.simulator.network.api.node.NodeId
import org.opendc.simulator.network.utils.NonSerializable

/**
 * Network that is not built following a specific algorithm.
 * It can be built from JSON with specific format.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class CustomNetwork(
    nodes: List<Node> = mutableListOf(),
) : Network() {
    companion object {
        val log by logger()
    }

    override val nodesById: MutableMap<NodeId, Node> = nodes.associateBy { it.id }.toMutableMap()

    override val endPointNodes: MutableMap<NodeId, EndPointNode> =
        nodes.filterIsInstance<EndPointNode>()
            .associateBy { it.id }.toMutableMap()

    override val internet: Internet =
        Internet().connectedTo(
            coreSwitches = this.nodesById.values.filterIsInstance<CoreSwitch>(),
        )

    private val onNodeAdded = mutableListOf<(Network, Node) -> Unit>()

    private val onNodeRemoved = mutableListOf<(Network, Node) -> Unit>()

    init {
        this.nodesById[internet.id] = internet
        this.endPointNodes[internet.id] = internet
    }

    internal fun onNodeAdded(callback: (Network, Node) -> Unit) {
        onNodeAdded.add(callback)
    }

    internal fun onNodeRemoved(callback: (Network, Node) -> Unit) {
        onNodeRemoved.add(callback)
    }

    /**
     * Connects [Node]s of ***this*** based on link-list passed as parameter.
     * If the link cannot be established, a warning message is logged and the link is ignored.
     * @param[links]    the list of pair representing the links to be established in the network.
     */
    fun connectFromLinkList(links: List<Pair<NodeId, NodeId>>) =
        runBlocking {
            fun warnOfUnsetLink(
                id1: NodeId,
                id2: NodeId,
            ) {
                log.warn(
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
                node1.connect(node2)
            }
        }

    /**
     * Adds a node to the network if a node with same id is not already present.
     * @param[node] node to add.
     * @return [node] if it was added, `null` otherwise.
     */
    fun addNode(node: Node): Node? {
        if (node.id == internet.id) return log.errAndNull("unable to add node $node, id is reserved for internet abstraction")
        if (this.isRunning) {
            runBlocking {
                val job = Job(runnerJob)
                launch(job) {
                    node.run(invalidator = this@CustomNetwork.validator.Invalidator())
                }
            }
        }

        nodesById[node.id]?.let { return log.errAndNull("unable to add node $node, id already present") }
        nodesById[node.id] = node
        (node as? EndPointNode)?.let { endPointNodes[node.id] = node }
        (node as? CoreSwitch)?.let { runBlocking(validator) { it.connect(internet) } }
        onNodeAdded.forEach { it.invoke(this, node) }

        return node
    }

    /**
     * Removes a node from the network, and all its generated [NetFlow]s.
     * @return the removed [Node] if any, `null` otherwise.
     */
    suspend fun rmNode(nodeId: NodeId): Node? =
        nodesById[nodeId]?. also {
            it.disconnectAll()
            rmEtoEFlowsGenBy(nodeId)
            nodesById.remove(nodeId)
                ?.let { node ->
                    onNodeRemoved.forEach { callback ->
                        callback.invoke(this, node)
                    }
                }
        } ?: log.errAndNull("unable to remove node, not present in the network")

    /**
     * Removes all [NetFlow]s generated by node with id [nodeId].
     */
    private fun rmEtoEFlowsGenBy(nodeId: NodeId) {
        flowsById.values.removeAll { it.transmitterId == nodeId }
    }

    override fun toSpecs(): Specs<CustomNetwork> {
        val links: List<Pair<NodeId, NodeId>> =
            buildList {
                val nodes = nodesById.values.filterNot { it is Internet }
                val doneNodes = mutableSetOf<NodeId>()

                nodes.forEach { node ->
                    addAll(
                        node.portToNode.keys
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

    /**
     * [Specs] of [CustomNetwork], deserializable from JSON.
     * From ***this*** the corresponding custom network can be built.
     */
    @Serializable
    @SerialName("custom-network-topology-specs")
    internal data class CustomNetworkSpecs(
        val nodesSpecs: List<Specs<Node>>,
        @Serializable(with = LinkListSerializer::class)
        val links: List<Pair<NodeId, NodeId>>,
    ) : Specs<CustomNetwork> {
        override fun build(): CustomNetwork {
            val nodes: List<Node> = nodesSpecs.map { it.build() }
            val distinctNodes = nodes.distinctBy { it.id }
            if (nodes.size != distinctNodes.size) {
                log.warn("Some nodesById with already existing ids got filtered out.")
            }
            val customNetwork = CustomNetwork(distinctNodes)
            customNetwork.connectFromLinkList(links)
            return customNetwork
        }

        companion object {
            val log by logger()
        }
    }

    /**
     * Deserializer of a JSON array of array (**size 2**) of ints `[[1, 2], [2, 3]]`,
     * into a link list (`List<Pair<NodeID, NodeId2>>`).
     * - Filters out arrays (links) with size not equal to 2.
     * - Filters out links that try to connect a node to itself.
     */
    private class LinkListSerializer : KSerializer<List<Pair<NodeId, NodeId>>> {
        companion object {
            private val log by logger()
        }

        override val descriptor: SerialDescriptor = serialDescriptor<List<Pair<NodeId, NodeId>>>()

        override fun deserialize(decoder: Decoder): List<Pair<NodeId, NodeId>> {
            val listOfArrays: List<List<NodeId>> =
                decoder.decodeSerializableValue(kotlinx.serialization.serializer())
            val linkList: List<Pair<NodeId, NodeId>> =
                listOfArrays
                    .filterNot {
                        if (it.size != 2) {
                            log.warn("Invalid link represented by array $it, size of array should be 2")
                            return@filterNot true
                        } else {
                            false
                        }
                    }
                    .map { it[0] to it[1] }
                    .filterNot {
                        if (it.first == it.second) {
                            log.warn("Invalid link $it, a node may not connect to itself")
                            return@filterNot true
                        } else {
                            false
                        }
                    }

            return linkList
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun serialize(
            encoder: Encoder,
            value: List<Pair<NodeId, NodeId>>,
        ) {
            // Serialize JsonPrimitive Literal to avoid pretty print of the arrays & quotes
            val noPrettySerializedLinks =
                value.map {
                    JsonUnquotedLiteral(Json.encodeToString(listOf(it.first, it.second)))
                }

            val serializer = ListSerializer(serializer<JsonPrimitive>())
            encoder.encodeSerializableValue(serializer, noPrettySerializedLinks)
        }
    }
}
