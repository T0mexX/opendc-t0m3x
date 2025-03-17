package org.opendc.simulator.network.utils.invalidatable

internal class MutableInvalidatorFlow<T>: InvalidatorFlow<T>() {
    internal suspend fun emit(obj: T) {
        if (obj is Invalidatable) obj.invalidate()
        inner.emit(obj)
    }
}
