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

package org.opendc.simulator.network.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A dummy serializer which can be applied to types that are never expected to be serialized,
 * but for which the compiler is trying to generate/retrieve a serializer. E.g., types used as generic type parameters.
 * Applying `@Serializable( with = NotSerializable::class )` to those types ensures compilation succeeds,
 * without having to actually make them serializable.
 */
internal object NonSerializable : KSerializer<Any> {
    private val exception =
        SerializationException(
            "Types annotated as `@Serializable( with = NotSerializable::class )` are never expected to be serialized. " +
                "The serializer is only defined since the compiler does not know this, causing a compilation error.",
        )
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NonSerializable", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Any = throw exception

    override fun serialize(
        encoder: Encoder,
        value: Any,
    ): Unit = throw exception
}
