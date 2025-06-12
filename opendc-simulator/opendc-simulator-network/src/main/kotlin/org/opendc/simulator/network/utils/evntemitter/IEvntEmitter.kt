package org.opendc.simulator.network.utils.evntemitter

import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable

internal interface IEvntEmitter<Self: EvntEmitter<Self>> : EvntEmitter<Self> {
    val nCollectors: Int

    suspend fun emit(evnt: EvntImpl<Self, *>)

    companion object {
        operator fun <Self: IEvntEmitter<Self>> invoke(): IEvntEmitter<Self> =
            object : IEvntEmitter<Self> {
                private val collectors = mutableListOf<EvntCollector<Self>>()
                override val nCollectors: Int get() = collectors.size

                override suspend fun collector(): EvntCollector<Self> =
                    EvntCollector<Self>().also { collectors.add(it) }

                override suspend fun emit(evnt: EvntImpl<Self, *>) {
                    evnt.nCollectors = nCollectors
                    (evnt as? Invalidatable)?.invalidate()
                    collectors.forEach { c -> c.sendChl.send(evnt) }
                }
            }
    }
}
