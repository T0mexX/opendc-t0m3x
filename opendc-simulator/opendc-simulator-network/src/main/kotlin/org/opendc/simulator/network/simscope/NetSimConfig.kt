package org.opendc.simulator.network.simscope

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


//TODO: Make serializer
/**
 * Configuration of [NetSimScope].
 * It encapsulates simulation settings that can be managed externally.
 */
public data class NetSimConfig(
    val stabilityChecks: Boolean = false,
): AbstractCoroutineContextElement(Key) {

    public companion object Key : CoroutineContext.Key<NetSimConfig> {
        public val DEFAULT: NetSimConfig = NetSimConfig(
            stabilityChecks = false
        )
    }
}
