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

package org.opendc.simulator.network.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.common.units.Timestamp.Companion.toTimestamp
import org.opendc.simulator.network.api.workload.NetWorkload
import org.opendc.simulator.network.input.readNetworkWl
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScopeSpec
import javax.naming.OperationNotSupportedException

/**
 * TODO
 */
@Serializable(with = NetSimExp.NetSimExpSerializer::class)
public data class NetSimExp internal constructor(
    internal val rootScope: NetSimRootScope,
    internal val wl: NetWorkload,
    internal val virtualMapping: Boolean,
) {
    /**
     * TODO
     */
    public fun runner(): NetSimWlRunner {
        // TODO Use `virtualMapping`

        return NetSimWlRunner(rootScope, wl)
    }

    /**
     * TODO
     */
    internal class NetSimExpSerializer : KSerializer<NetSimExp> {
        @Serializable
        private data class NetSimExpSurrogate(
            val wlPath: String,
            val seed: Long? = null,
            val ctxSpec: NetSimScopeSpec,
            val virtualMapping: Boolean = true,
        )

        private val surrogateSerial: KSerializer<NetSimExpSurrogate> = kotlinx.serialization.serializer()

        override val descriptor: SerialDescriptor = serialDescriptor<NetSimExpSurrogate>()

        override fun deserialize(decoder: Decoder): NetSimExp {
            val surr: NetSimExpSurrogate = decoder.decodeSerializableValue(surrogateSerial)

            // Read the network workload.
            val wl = readNetworkWl(surr.wlPath)

            //
            // Set up network rootScope deserialization context.
            val spec =
                surr.ctxSpec
                    .withInjectedInitialTmstamp(wl.startInstant.toTimestamp())
                    .let {
                        if (surr.seed != null) {
                            it.withInjectedSeed(surr.seed)
                        } else {
                            it
                        }
                    }

            //
            // Deserialize network simulation rootScope.
            val rootScope = NetSimRootScope(spec = spec)

            return NetSimExp(
                wl = readNetworkWl(surr.wlPath),
                rootScope = rootScope,
                virtualMapping = surr.virtualMapping,
            )
        }

        override fun serialize(
            encoder: Encoder,
            value: NetSimExp,
        ) {
            throw OperationNotSupportedException()
        }
    }
}
