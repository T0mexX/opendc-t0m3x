package org.opendc.simulator.network.utils

public fun interface ChangeHndlr<O, T> {
    public fun handle(obj: O, oldValue: T, newValue: T)
}

public fun interface ChangeHndlrSus<O, T> {
    public suspend fun handle(obj: O, oldValue: T, newValue: T)
}
