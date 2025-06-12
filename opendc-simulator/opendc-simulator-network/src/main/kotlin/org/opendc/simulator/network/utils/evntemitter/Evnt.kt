package org.opendc.simulator.network.utils.evntemitter

import org.opendc.simulator.network.utils.InternalODCNetworkApi

public interface Evnt<T: EvntEmitter<T>, Self: Evnt<T, Self>> {
    public suspend fun handled()

    /**
     * TODO
     * @param builderBlock
     */
    @InternalODCNetworkApi
    public suspend fun reset(builderBlock: (suspend Self.() -> Unit)? = null): Self
}


internal fun <T: EvntEmitter<T>, Self: Evnt<T, Self>> Evnt<T, Self>.iEvnt(): IEvnt<T, Self>
