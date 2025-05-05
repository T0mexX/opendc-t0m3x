package org.opendc.simulator.network.components.specs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import org.opendc.common.logger.logger
import org.opendc.simulator.network.components.networks.CustomNetwork
import org.opendc.simulator.network.components.node.SerializableNode
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * [Specs] of [CustomNetwork], deserializable from JSON.
 * From ***this*** the corresponding custom network can be built.
 */
@Serializable
@SerialName("custom-specs")
internal data class CustomNetworkSpecs(
    val nodesSpecs: List<Specs<SerializableNode>> = emptyList(),
    @Serializable(with = LinkListSerializer::class)
    val links: List<Pair<NodeId, NodeId>> = emptyList(),
) : Specs<CustomNetwork> {
    context(NetSimScope)
    override suspend fun build(): CustomNetwork {
        val nodes: List<Node<*>> = nodesSpecs.map { it.build().asNode() }
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

    /**
     * Deserializer of a JSON array of array (**size 2**) of ints `[[1, 2], [2, 3]]`,
     * into a link list (`List<Pair<NodeID, NodeId2>>`).
     * - Filters out arrays (links) with size not equal to 2.
     * - Filters out links that try to connect a node to itself.
     */
    private class LinkListSerializer : KSerializer<List<Pair<NodeId, NodeId>>> {
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
