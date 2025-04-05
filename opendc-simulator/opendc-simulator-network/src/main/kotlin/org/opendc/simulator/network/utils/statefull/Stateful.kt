package org.opendc.simulator.network.utils.statefull

import kotlinx.coroutines.flow.StateFlow

internal interface Stateful<T: Stateful<T>> {
    val state: StateFlow<State<T>>
}
