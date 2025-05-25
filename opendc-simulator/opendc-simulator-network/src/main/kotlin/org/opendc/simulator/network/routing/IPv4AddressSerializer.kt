package org.opendc.simulator.network.routing

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * TODO
 */
internal class IPv4AddressSerializer : OnlyString<IPv4Address>(
    object : KSerializer<IPv4Address> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IPv4Address", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: IPv4Address) {
            encoder.encodeString(value.toNormalizedString())
        }

        override fun deserialize(decoder: Decoder): IPv4Address {
            val str = decoder.decodeString()
            return kotlin.runCatching {
                // If in string standard format.
                IPAddressString(str).address as IPv4Address

                // If in integer format.
            }.getOrNull() ?: IPv4Address(Json.decodeFromString<Int>(str))
        }
    }
)

/**
 * Allows manipulating an abstract JSON representation of the class before serialization or deserialization.
 * Maps a [JsonPrimitive] to its [String] representation.
 *
 * ```json
 * // e.g.
 * "value": 3
 * // for deserialization becomes
 * "value": "3"
 */
internal open class OnlyString<T : Any>(tSerial: KSerializer<T>) : JsonTransformingSerializer<T>(tSerial) {
    override fun transformDeserialize(element: JsonElement): JsonElement = JsonPrimitive(element.toString().trim('"'))
}
