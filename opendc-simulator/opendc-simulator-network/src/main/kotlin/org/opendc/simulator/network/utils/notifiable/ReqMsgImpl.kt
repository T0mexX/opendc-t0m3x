package org.opendc.simulator.network.utils.notifiable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.flyweight.internals.FWPool
import org.opendc.simulator.network.utils.flyweight.publics.FWId

/**
 * TODO
 */
internal abstract class ReqMsgImpl<T: Msgable<T>, A, Self: ReqMsg<T, A, Self>>(
    pool: FWPool<Self, FWId<Self>>,
    poolIdx: Idx
) : ReqMsg<T, A, Self>, MsgImpl<T, Self>(pool, poolIdx) {
    /**
     * TODO
     */
    private val resp = MutableStateFlow<A?>(null)

    /**
     * TODO
     */
    override suspend fun awaitResponse(): A =
        resp.first {
            it != null
        }!!

    /**
     * TODO
     */
    final override suspend fun reset(): Self {
        state.emit(Msg.State.UNTRACKED)
        sender = null
        resp.emit(null)

        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    suspend fun respond(response: A) {
        resp.emit(response)
    }
}
