package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
public value class NodeId(public val value: Long) {
    internal operator fun inc(): NodeId = NodeId(this.value + 1)
    internal operator fun compareTo(other: NodeId): Int = this.value.compareTo(other.value)

    override fun toString(): String = value.toString()

    internal fun toIp(): IPv4Address = IPv4Address(value.toInt())

    public companion object {
        public val INVALID: NodeId = NodeId(-1)
    }
}
