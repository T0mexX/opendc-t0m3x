package org.opendc.simulator.network.utils.flyweight.internals

import kotlinx.serialization.Serializable

/**
 * TODO
 * @property subPoolMaxSize CAn have performance impact.
 */
@Serializable
internal data class FWConfig(
    val nSubPools: Int = 10,
    val initialCapacity: Int = 100,
    val poolMaxSize: Int? = null,
    val subPoolMaxSize: Int? = null,
    val subPoolMaxIdle: Int? = null,
    // TODO: not used yet (always throw)
//    val throwOnMaxSizeExceeded: Boolean = false,
)
