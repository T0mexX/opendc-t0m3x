package org.opendc.simulator.network.utils.tracker

internal interface Trackable<T: Trackable<T>> {
    var tracker: Tracker<T>?

    @Suppress("UNCHECKED_CAST")
    fun untrack(): T {
        tracker!!.remove(this as T)
        return this
    }
}
