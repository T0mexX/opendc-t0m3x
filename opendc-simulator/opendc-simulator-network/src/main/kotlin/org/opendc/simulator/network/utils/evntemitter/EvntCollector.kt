package org.opendc.simulator.network.utils.evntemitter

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

public class EvntCollector<T: EvntEmitter<T>> private constructor(
    chl: Channel<Evnt<T, *>>,
) : ReceiveChannel<Evnt<T, *>> by chl {
    internal constructor() : this(Channel())

    internal val sendChl: SendChannel<Evnt<T, *>> = chl
}
