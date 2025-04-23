package org.opendc.simulator.network.components.specs

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.SerializableNode
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal interface NodeSpecs<T>: Specs<T>
    where T: SerializableNode {

    /**
     * TODO
     */
    context(NetSimScope)
    fun nPorts(): Int

    /**
     * TODO
     */
    context(NetSimScope)
    fun portSpeed(): DataRate

    /**
     * TODO
     */
    context(NetSimScope)
    fun fairnessPolicy(): FairnessPolicy
}
