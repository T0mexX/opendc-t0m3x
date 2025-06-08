package org.opendc.simulator.network.components.networks.ftree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.simscope.NetConfig

/**
 * TODO
 * @property buildSteps Determines when routing information is propagated during the building phase:
 * - 1: routing information is propagated every time a node is added and/or connected.
 * - 2: routing information is propagated once the whole network has been built.
 * - 3: routing information is propagated both when a [FTree.FTreePod], and when the whole network has been built.
 *
 * This can have a huge impact on the performance of the building step.
 *
 * @property subnets Determines if each pod receives its own subnets (each node
 * not in subnet S will treat S as a single entry for routing decision, reducing the space utilization).
 * Setting this option to `false` (in a fat-tree) can change routing behaviour only in case of failures
 * of some nodes, in that case, a complete routing table can deal better with the missing path.
 * Hence, if failure is not part of the simulation, there is no reason to set it to `false`.
 */
@Serializable
internal class FTreeConfig(
    val buildSteps: Int = 1,
    // TODO: not used yet
    val subnets: Boolean = true,
) {
    init {
        require(buildSteps in 1..3)
    }
}

