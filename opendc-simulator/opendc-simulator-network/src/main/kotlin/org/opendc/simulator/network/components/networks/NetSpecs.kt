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

package org.opendc.simulator.network.components.networks

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NETWORK_JSON
import java.io.File

internal interface NetSpecs<T : Network<T>> {
    /**
     * Number of routers (switches) in the network.
     */
    @Suppress("ktlint:standard:property-naming")
    val R_: Int

    /**
     * Number of terminals (hosts) in the network.
     */
    val N_: Int

    /**
     * Number of vertices (nodes) in the network.
     */
    val V_: Int

    /**
     * Number of edges (links) in the network.
     */
    val E_: Int

    /**
     * Builds the corresponding [T] object.
     */
    context(NetSimScope)
    suspend fun build(): T

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun fromFile(file: File): NetSpecs<*> = NETWORK_JSON.decodeFromStream(file.inputStream())

        fun fromFile(filePath: String): NetSpecs<*> = fromFile(File(filePath))
    }
}
