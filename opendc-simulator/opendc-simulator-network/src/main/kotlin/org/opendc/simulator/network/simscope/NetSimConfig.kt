package org.opendc.simulator.network.simscope

import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


//TODO: Make serializer
/**
 * Configuration of [NetSimScope].
 * It encapsulates simulation settings that can be managed externally.
 */
public data class NetSimConfig internal constructor(
    val stabilityMode: NetSimStabilityMode = NetSimStabilityMode.ASSUMED,
    val exportConfig: NetworkExportConfig? = null,
    internal val netSimDevConfig: NetSimDevConfig = NetSimDevConfig(),
): AbstractCoroutineContextElement(Key) {

    public companion object Key : CoroutineContext.Key<NetSimConfig> {
        public val DEFAULT: NetSimConfig = NetSimConfig(
            stabilityMode = NetSimStabilityMode.ASSUMED,
            exportConfig = null
        )
    }
}
