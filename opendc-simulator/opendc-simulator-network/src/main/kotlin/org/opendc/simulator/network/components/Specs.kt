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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

/**
 * Type serializable from json, representing the specifics of concrete object of type `T`.
 * The object of type `T` can then be built with [build].
 */
@Serializable
public sealed interface Specs<out T : WithSpecs<in @UnsafeVariance T>> {
    /**
     * Builds the corresponding [T] object.
     */
    public fun build(): T

    public companion object {
        @OptIn(ExperimentalSerializationApi::class)
        public inline fun <reified T : WithSpecs<T>> fromFile(file: File): Specs<T> {
            val json = Json { ignoreUnknownKeys = true }

            return json.decodeFromStream(file.inputStream())
        }

        public inline fun <reified T : WithSpecs<T>> fromFile(filePath: String): Specs<T> = fromFile(File(filePath))
    }
}
