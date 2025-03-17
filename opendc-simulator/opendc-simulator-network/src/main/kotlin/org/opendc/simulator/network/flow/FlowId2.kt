package org.opendc.simulator.network.flow

@JvmInline
public value class FlowId2(public val value: Long) {




    internal companion object {
        val INVALID = FlowId2(-1)
    }
}
