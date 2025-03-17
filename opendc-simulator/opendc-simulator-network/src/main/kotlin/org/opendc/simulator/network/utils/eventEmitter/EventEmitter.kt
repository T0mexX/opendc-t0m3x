package org.opendc.simulator.network.utils.eventEmitter

import org.opendc.simulator.network.utils.invalidatable.InvalidatorFlow

public interface EventEmitter<T: EventEmitter<T>> {
    public val eventFlow: InvalidatorFlow<Event<T>>
}
