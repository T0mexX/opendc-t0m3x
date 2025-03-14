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

package org.opendc.simulator.network.simscope.ip

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

        override fun serialize(
            encoder: Encoder,
            value: IPv4Address,
        ) {
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
    },
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
