package org.opendc.simulator.network.components.networks

import org.opendc.simulator.network.components.specs.Specs

internal interface NetworkSpecs<T: Network>: Specs<T> {
    /**
     * Common networking parameters use both uppercase and lowercase letters in academic papers,
     * often with distinct meanings. To avoid overly verbose property names and potential JVM
     * signature conflicts, underscores are used to preserve clarity while keeping names short.
     */

    /**
     * Number of routers (switches) in the network.
     */
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
}
