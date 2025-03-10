package org.opendc.simulator.network.utils

import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Key.getSeqChl
import org.opendc.simulator.network.api.integration.SeqComputeIntegration.Mode
import kotlin.coroutines.coroutineContext

internal class HndlrsLs<O, T>(private val mode: Mode = Mode.BOTH) {

    private val seq by lazy { mutableListOf<ChangeHndlr<O, T>>() }
    private val sus by lazy { mutableListOf<SusChangeHndlr<O, T>>() }

    internal fun addSeq(f: ChangeHndlr<O, T>) {
        if (mode == Mode.BOTH || mode == Mode.SEQUENTIAL) {
            seq.add(f)
        }
    }

    internal fun addSus(f: SusChangeHndlr<O, T>) {
        if (mode == Mode.BOTH || mode == Mode.SUSPENDING) {
            sus.add(f)
        }
    }

    internal suspend fun handleAll(obj: O, old: T, new: T) {
        if ((mode == Mode.SEQUENTIAL || mode == Mode.BOTH) && seq.isNotEmpty()) {
            val chl = coroutineContext.getSeqChl()
            seq.forEach { chl.send(it.lazy(obj, old, new)) }
        }
        if (mode == Mode.SUSPENDING || mode == Mode.BOTH) {
            sus.forEach { it.handle(obj, old, new) }
        }
    }
}
