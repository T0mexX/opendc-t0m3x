package org.opendc.simulator.network.flow.publics

@JvmInline
public value class FlowId(public val value: Long) {
    internal operator fun inc(): FlowId = FlowId(this.value + 1)
    internal operator fun compareTo(other: FlowId): Int = this.value.compareTo(other.value)



    internal companion object {
        val INVALID = FlowId(-1)
    }
}
