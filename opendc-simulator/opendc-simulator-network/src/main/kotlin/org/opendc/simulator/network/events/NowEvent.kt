package org.opendc.simulator.network.events

internal fun interface NowEvent<T: WithEvents<T>>: Event<T>
