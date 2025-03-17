package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.node.NodeId2

@JvmInline
public value class FlowId2(public val value: Long) {
    internal operator fun inc(): FlowId2 = FlowId2(this.value + 1)
    internal operator fun compareTo(other: FlowId2): Int = this.value.compareTo(other.value)



    internal companion object {
        val INVALID = FlowId2(-1)
    }
}
