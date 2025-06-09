package org.opendc.simulator.network.components.networks.ftree

import kotlinx.serialization.Serializable

/**
 * TODO
 * @property buildSteps Determines when routing information is propagated during the building phase:
 * - 1: routing information is propagated every time a node is added and/or connected.
 * - 2: routing information is propagated once the whole network has been built.
 * - 3: routing information is propagated both when a [FTree.FTreePod], and when the whole network has been built.
 *
 * This can have a huge impact on the performance of the building step.
 *
 */
@Serializable
internal class FTreeConfig(
    val buildSteps: Int = 1,
) {
    init {
        require(buildSteps in 1..3)
    }
}

