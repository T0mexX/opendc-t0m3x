package org.opendc.simulator.network.utils.statefull.publics

import kotlinx.coroutines.flow.StateFlow

public interface Stateful<T: Stateful<T>> {
    public val state: StateFlow<State<T>>
}
