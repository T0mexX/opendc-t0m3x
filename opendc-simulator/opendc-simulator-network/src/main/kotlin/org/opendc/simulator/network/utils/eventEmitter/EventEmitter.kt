package org.opendc.simulator.network.utils.eventEmitter

import kotlinx.coroutines.flow.SharedFlow

public interface EventEmitter<T: org.opendc.simulator.network.utils.eventEmitter.EventEmitter<T>> {
    val eventFlow: SharedFlow<org.opendc.simulator.network.utils.eventEmitter.Event<T>>
}
