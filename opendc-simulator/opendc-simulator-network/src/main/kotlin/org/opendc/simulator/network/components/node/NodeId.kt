package org.opendc.simulator.network.components.node

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
public value class NodeId(public val value: Long) {
    internal operator fun inc(): NodeId = NodeId(this.value + 1)
    internal operator fun compareTo(other: NodeId): Int = this.value.compareTo(other.value)

    public companion object {
        public val INVALID: NodeId = NodeId(-1)
    }
}
