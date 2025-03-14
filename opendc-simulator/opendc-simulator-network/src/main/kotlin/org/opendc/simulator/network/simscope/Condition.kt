package org.opendc.simulator.network.simscope

import kotlinx.coroutines.flow.MutableStateFlow

internal class Condition(initialValue: Boolean) {
    private val state = MutableStateFlow(initialValue)

    internal suspend fun signal() {
        state.emit(true)
    }

    internal suspend fun reset() {
    }

    internal suspend fun await(value: Boolean) {

    }
}
