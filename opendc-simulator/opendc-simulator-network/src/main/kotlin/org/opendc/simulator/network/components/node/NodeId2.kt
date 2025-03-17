package org.opendc.simulator.network.components.node

@JvmInline
public value class NodeId2(public val value: Long) {
    internal operator fun inc(): NodeId2 = NodeId2(this.value + 1)
    internal operator fun compareTo(other: NodeId2): Int = this.value.compareTo(other.value)
}
