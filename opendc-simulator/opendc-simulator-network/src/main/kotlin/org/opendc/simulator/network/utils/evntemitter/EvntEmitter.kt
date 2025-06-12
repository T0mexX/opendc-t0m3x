package org.opendc.simulator.network.utils.evntemitter

public interface EvntEmitter<out Self: EvntEmitter<Self>> {
    public suspend fun collector(): EvntCollector<out Self>
}
