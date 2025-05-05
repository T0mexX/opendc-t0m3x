package org.opendc.simulator.network.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.workload.NetWorkload
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.input.readNetworkWl
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimTmSrc
import javax.naming.OperationNotSupportedException

/**
 * TODO
 */
@Serializable(with = NetSimExp.NetSimExpSerializer::class)
public data class NetSimExp internal constructor(
    internal val networkSpecs: Specs<Network>,
    internal val wl: NetWorkload,
    internal val netSimConfig: NetSimConfig = NetSimConfig(),
) {
    /**
     * TODO
     */
    public suspend fun runner(): NetSimWlRunner {
        // Create simulation scope.
        val scope = NetSimScope(
            // Add configurations to scope (including `NetSimDevConfig`).
            netSimConfig +
                // Add an internally managed simulation virtual time source,
                // starting at the first deadline of the workload.
                NetSimTmSrc.Internal(
                    initialTmStamp = Timestamp.ofInstant(wl.startInstant)
                )
        )

        with(scope) {
            // Build network in the scope of the simulation,
            // so that all coroutines running the network components
            // are in the simulation scope and can be canceled hierarchically.
            networkSpecs.build()
        }

        return NetSimWlRunner(scope, wl)
    }


    /**
     * TODO
     */
    internal class NetSimExpSerializer : KSerializer<NetSimExp> {
        @Serializable
        private data class NetSimExpSurrogate(
            val networkSpecsPath: String,
            val wlPath: String,
            val netSimConfig: NetSimConfig = NetSimConfig(),
        )

        private val surrogateSerial: KSerializer<NetSimExpSurrogate> = kotlinx.serialization.serializer()

        override val descriptor: SerialDescriptor = serialDescriptor<NetSimExpSurrogate>()

        override fun deserialize(decoder: Decoder): NetSimExp {
            val surrogate: NetSimExpSurrogate = decoder.decodeSerializableValue(surrogateSerial)

            return NetSimExp(
                networkSpecs = Specs.fromFile(surrogate.networkSpecsPath),
                wl = readNetworkWl(surrogate.wlPath),
                netSimConfig = surrogate.netSimConfig,
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
