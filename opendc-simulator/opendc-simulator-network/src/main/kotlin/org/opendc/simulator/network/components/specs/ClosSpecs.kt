package org.opendc.simulator.network.components.specs

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Clos
import org.opendc.simulator.network.components.node.GlobalSwitch
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.simscope.NetSimDevConfig
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * Specifications of a [Clos] network.
 *
 * @param n Number of switch layers of the Clos network.
 * @param nodesPerLayer Number of nodes per layer.
 * This map has `n + 1` entries, 1 for each switch layer + the host layer.
 * Each node in a layer is fully connected with the layers above/below.
 * The first layer is `0` and corresponds to [GlobalSwitch]s,
 * the last layer is [n] and will be filled with [HostNode]s.
 * @param portSpeedPerLayer Port speed of nodes for each layer.
 * Same mapping as [nodesPerLayer] applies.
 *
 * @see ClosSpecsSerializer for deserialization.
 */
@Serializable(with = ClosSpecsSerializer::class)
@SerialName("clos")
internal class ClosSpecs(
    val n: Int,
    val nodesPerLayer: Map<Int, Int>,
    val portSpeedPerLayer: Map<Int, DataRate>,
) : Specs<Clos> {
    context(NetSimScope) override suspend fun build(): Clos = Clos(this)
}

/**
 * Custom serializer for [ClosSpecs], dealing with missing
 * values for better usability, while maintaining immutability of [ClosSpecs]
 *
 * @see Surr for a serial descriptor.
 */
private class ClosSpecsSerializer : KSerializer<ClosSpecs> {
    /**
     * @param n Number of switch layers.
     * @param nodesPerLayer Maps the layers (0 to n) to the number of nodes in those layers.
     * Layer 0 is the uppermost, built of global switches. The lowest layer is [n], built of host nodes.
     * Missing values will be replaced by [dfltNodesPerLayer] if defined.
     * @param dfltNodesPerLayer The default value used for missing ones in [nodesPerLayer].
     * @param portSpeedPerLayer  Maps the layers (0 to n-1) to the port speed of the nodes in those layers.
     * Same mapping rules as [nodesPerLayer] apply.
     * Missing values will be replaced by [dfltPortSpeed] if provided,
     * or fall back to the default defined in [NetSimDevConfig]. If none of these are set, deserialization fails.
     * @param dfltPortSpeed The default value used for missing ones in [portSpeedPerLayer].
     */
    @Serializable
    @SerialName("clos")
    private data class Surr(
        val n: Int,
        val nodesPerLayer: Map<Int, Int>? = null,
        val dfltNodesPerLayer: Int? = null,
        val portSpeedPerLayer: Map<Int, DataRate>? = null,
        val dfltPortSpeed: DataRate? = null,
    )

    override val descriptor: SerialDescriptor = serialDescriptor<Surr>()

    override fun deserialize(decoder: Decoder): ClosSpecs {
        val surr = decoder.decodeSerializableValue(serializer<Surr>())

        // Build the map for the number of nodes per layer.
        val nPerL = buildMap {
            (0..surr.n).forEach { layer ->
                put(layer, surr.nodesPerLayer?.get(layer) ?: surr.dfltNodesPerLayer!!)
            }
        }

        // Build the map for the port speed per layer.
        val drPerL = buildMap {
            (0..surr.n).forEach { layer ->
                put(layer, surr.portSpeedPerLayer?.get(layer) ?: surr.dfltPortSpeed!!)
            }
        }

        return ClosSpecs(
            n = surr.n,
            nodesPerLayer = nPerL,
            portSpeedPerLayer = drPerL,
        )
    }

    override fun serialize(encoder: Encoder, value: ClosSpecs) {
        // The most frequent number of nodes among the layers, to be used as default.
        val nPerLMode = value.nodesPerLayer.values.groupBy { it }
            .maxBy { it.value.size }
            // Use a default only if multiple layers use the same value.
            .takeIf { it.value.size > 1 }
            ?.key

        // The most frequent port speed among the layers, to be used as default.
        val drPerLMode = value.portSpeedPerLayer.values.groupBy { it }
            .maxBy { it.value.size }
            // Use a default only if multiple layers use the same value.
            .takeIf { it.value.size > 1 }
            ?.key

        val nPerL = value.nodesPerLayer.filterNot { it.value == nPerLMode }.takeIf { it.isNotEmpty() }
        val drPerL = value.portSpeedPerLayer.filterNot { it.value == drPerLMode }.takeIf { it.isNotEmpty() }

        // Serialize the surrogate.
        encoder.encodeSerializableValue(
            serializer(),
            Surr(
                n = value.n,
                nodesPerLayer = nPerL,
                dfltNodesPerLayer = nPerLMode,
                portSpeedPerLayer = drPerL,
                dfltPortSpeed = drPerLMode
            )
        )
    }
}
