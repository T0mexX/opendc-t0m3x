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

/**
 * TODO
 */
internal class IPv4AddressSerializer : KSerializer<IPv4Address> {
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
