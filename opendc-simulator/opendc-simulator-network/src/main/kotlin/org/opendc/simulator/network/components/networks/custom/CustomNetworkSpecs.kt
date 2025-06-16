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

@file:Suppress("PropertyName")

package org.opendc.simulator.network.components.networks.custom

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
import org.opendc.common.logger.logger
import org.opendc.simulator.network.components.networks.NetSpecs
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.switchh.SwitchSpecs
import org.opendc.simulator.network.components.node.terminal.TerminalSpecs
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * [Specs] of [CustomNetwork], deserializable from JSON.
 * From ***this*** the corresponding custom network can be built.
 */
@Serializable
@SerialName("custom")
internal data class CustomNetworkSpecs(
    val terminalSpecs: List<TerminalSpecs> = emptyList(),
    val switchSpecs: List<SwitchSpecs> = emptyList(),
    @Serializable(with = LinkListSerializer::class)
    val links: List<Pair<NodeId, NodeId>> = emptyList(),
) : NetSpecs<CustomNetwork> {
    override val R_: Int = switchSpecs.size
    override val N_: Int = terminalSpecs.size
    override val V_: Int = R_ + N_
    override val E_: Int = links.size

    context(NetSimScope)
    override suspend fun build(): CustomNetwork = CustomNetwork(specs = this)

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
