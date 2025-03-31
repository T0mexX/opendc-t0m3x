package org.opendc.simulator.network.utils.`eventEmitter-old`.publics

import org.opendc.simulator.network.utils.invalidatable.internals.InvalidatorFlow

public interface EventEmitter<T: EventEmitter<T>> {
    public val eventFlow: InvalidatorFlow<Event<T>>
}
