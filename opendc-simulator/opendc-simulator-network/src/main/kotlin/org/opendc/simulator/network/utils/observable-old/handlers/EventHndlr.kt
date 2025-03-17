package org.opendc.simulator.network.utils.`observable-old`.handlers

import org.opendc.simulator.network.utils.`observable-old`.Observable

public fun interface EventHndlr<T: Observable<T>> {
    public fun handle(obj: T)
}

public fun <T: Observable<T>> EventHndlr<T>.lazy(obj: T): LazyEventHndlr<T> = LazyEventHndlr(obj, this)

public fun interface SusEventHndlr<T: Observable<T>> {
    public suspend fun handle(obj: T)
}

public class LazyEventHndlr<T: Observable<T>>(private val obj: T, private val hndlr: EventHndlr<T>) {
    public fun handle() {
        hndlr.handle(obj)
    }
}
