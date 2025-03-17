package org.opendc.simulator.network.utils.invalidatable

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

public open class InvalidatorFlow<T> internal constructor() {
    protected val inner: MutableSharedFlow<T> = MutableSharedFlow()

    public suspend fun first(condition: (T) -> Boolean): T {
        var obj: T
        do {
            obj = inner.first()
            if (obj is Invalidatable) obj.validate()
        } while (condition(obj).not())

        return obj
    }
}
