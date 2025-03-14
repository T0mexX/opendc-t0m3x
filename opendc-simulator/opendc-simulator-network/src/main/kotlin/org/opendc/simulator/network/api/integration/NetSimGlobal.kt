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

package org.opendc.simulator.network.api.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.opendc.simulator.network.utils.NETWORK_JSON
import org.opendc.simulator.network.utils.NETWORK_SERIALIZERS_MODULE
import org.opendc.simulator.network.utils.SetOnce

/**
 * TODO
 */
public object NetSimGlobal {
    /**
     * Indicates whether the simulation is running in compute-network mode.
     *
     * This flag must be set exactly once at initialization. It controls conditional logic throughout the simulation
     * pipeline, particularly for enforcing constraints on configuration.
     */
    @Suppress("ktlint:standard:property-naming")
    public var WITH_COMPUTE: Boolean by SetOnce()

    public val NETSIMSCOPE_ROOT_CONAME: String = "NetMain"

    public object Serialization {
        /**
         * The [SerializersModule] used for JSON serialization of network-related types.
         *
         * If network components need to be deserialized, one can add this module to the current one with:
         * ```kotlin
         * val newModule = currModule + NetSimGlobal.SERIALIZERS_MODULE
         * ```
         */
        public val SERIALIZERS_MODULE: SerializersModule = NETWORK_SERIALIZERS_MODULE

        /**
         * The preconfigured [Json] instance used for serialization/deserialization of network data.
         *
         */
        public val JSON: Json = NETWORK_JSON
    }
//
//
//
//    /**
//     * The output folder where network export data will be written.
//     *
//     * This property is set in one of two ways:
//     * - Deserialized from [NetworkExportConfig] when running in network-only mode.
//     * - Manually assigned by another module during lazy initialization of network exporters.
//     *
//     * This value is write-once: it can be assigned only a single time, and any further attempt to modify it will result in an error.
//     */
//    public var OUTPUT_FOLDER: File by SetOnce()
}
