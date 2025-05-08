package org.opendc.simulator.network.simscope

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.Proportional
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import javax.naming.OperationNotSupportedException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


//TODO: Make serializer
/**
 * Configuration of [NetSimScope].
 * It encapsulates simulation settings that can be managed externally.
 */
@Serializable(with = NetSimConfig.NetSimConfigSerializer::class)
public data class NetSimConfig internal constructor(
    val stabilityMode: NetSimStabilityMode = NetSimStabilityMode.ASSUMED,
    val netSimExportConfig: NetworkExportConfig? = null,
    internal val routPolicy: RoutPolicy = ECMP(),
    internal val fairPolicy: FairnessPolicy = Proportional(),
    internal val netSimDevConfig: NetSimDevConfig = NetSimDevConfig(),
    val wlToNetIdMapping: Boolean = true,
): AbstractCoroutineContextElement(Key) {

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
            @Polymorphic val routingPolicy: RoutPolicy = ECMP(),
            @Polymorphic val fairnessPolicy: FairnessPolicy = Proportional(),
        )

        private val surrogateSerial: KSerializer<NetSimConfigSurrogate> = kotlinx.serialization.serializer()

        override val descriptor: SerialDescriptor = serialDescriptor<NetSimConfigSurrogate>()

        override fun deserialize(decoder: Decoder): NetSimConfig {
            val surrogate: NetSimConfigSurrogate = decoder.decodeSerializableValue(surrogateSerial)

            return NetSimConfig(
                stabilityMode = surrogate.stabilityMode,
                netSimExportConfig = surrogate.netSimExportConfig,
                netSimDevConfig = surrogate.netSimDevConfig,
                wlToNetIdMapping = surrogate.wlToNetIdMapping,
                routPolicy = surrogate.routingPolicy,
                fairPolicy = surrogate.fairnessPolicy,
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
