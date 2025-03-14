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

package org.opendc.simulator.network.simscope

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import javax.naming.OperationNotSupportedException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

// TODO: Make serializer

/**
 * Configuration of [NetSimCtxOld].
 * It encapsulates simulation settings that can be managed externally.
 */
@Serializable(with = NetSimConfig.NetSimConfigSerializer::class)
public data class
NetSimConfig internal constructor(
    val stabilityMode: NetSimStabilityMode = NetSimStabilityMode.ASSUMED,
    val exportConfig: NetworkExportConfig? = null,
    internal val routPolicy: RoutPolicy = ECMP(),
    internal val netSimDevConfig: NetSimDevConfig = NetSimDevConfig(),
    val random: Random = Random(Random.nextInt()),
    val wlToNetIdMapping: Boolean = true,
) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<NetSimConfig>

    /**
     * TODO
     * Needed since kserializer not working with class extending `Bastractcorotutinectxelem`
     */
    internal class NetSimConfigSerializer : KSerializer<NetSimConfig> {
        @Serializable
        private data class NetSimConfigSurrogate(
            val stabilityMode: NetSimStabilityMode = NetSimStabilityMode.ASSUMED,
            val netSimExportConfig: NetworkExportConfig? = null,
            val netSimDevConfig: NetSimDevConfig = NetSimDevConfig(),
            val wlToNetIdMapping: Boolean = true,
            val seed: Int? = null,
            @Polymorphic val routingPolicy: RoutPolicy = ECMP(),
        )

        private val surrogateSerial: KSerializer<NetSimConfigSurrogate> = kotlinx.serialization.serializer()

        override val descriptor: SerialDescriptor = serialDescriptor<NetSimConfigSurrogate>()

        override fun deserialize(decoder: Decoder): NetSimConfig {
            val surr: NetSimConfigSurrogate = decoder.decodeSerializableValue(surrogateSerial)

            return NetSimConfig(
                stabilityMode = surr.stabilityMode,
                exportConfig = surr.netSimExportConfig,
                netSimDevConfig = surr.netSimDevConfig,
                wlToNetIdMapping = surr.wlToNetIdMapping,
                routPolicy = surr.routingPolicy,
                random = surr.seed?.let { Random(it) } ?: Random(Random.nextLong()),
            )
        }

        override fun serialize(
            encoder: Encoder,
            value: NetSimConfig,
        ) {
            throw OperationNotSupportedException()
        }
    }
}
