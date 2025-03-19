package org.opendc.simulator.network.flow.internals

import kotlinx.serialization.Serializable

@Serializable
internal data class NetFlowConfig internal constructor(
    val version: NetFlowVersion = NetFlowV1
)
