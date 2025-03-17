package org.opendc.simulator.network.utils.statefull

import kotlinx.coroutines.flow.StateFlow

internal interface Stateful<T: org.opendc.simulator.network.utils.statefull.Stateful<T>> {
    val state: StateFlow<org.opendc.simulator.network.utils.statefull.State<T>>
}
