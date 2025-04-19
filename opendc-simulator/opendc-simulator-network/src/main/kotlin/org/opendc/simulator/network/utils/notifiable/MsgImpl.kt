package org.opendc.simulator.network.utils.notifiable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * TODO
 */
internal abstract class MsgImpl<T, Self: Msg<T, Self>> : Msg<T, Self>
    where T : Msgable<T> {
    /**
     * TODO
     */
    protected val state = MutableStateFlow(Msg.State.UNTRACKED)

    /**
     * TODO
     */
    protected var sender: CoroutineContext? = null

    /**
     * TODO
     */
    final override suspend fun awaitHandling(): Self {
        state.first {
            // If `state` is `null`, msg was sent with `dispose = true` which means
            // the message flyweight object might have been reused by now.
            check(sender == coroutineContext) { "await on recycled msg" }
            it == Msg.State.HANDLED
        }
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    final override suspend fun sendTo(to: T, dispose: Boolean): Self {
        // If dispose is false, `sender` wants to wait for the msg to be handled;
        // hence `state` is going to be tracked, and this `msg` is not going to be disposed by the receiver.
        if (dispose.not()) {
            sender = coroutineContext
            state.emit(Msg.State.PENDING)
        }

        to.msgChl.send(this)
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    final override suspend fun sendToPrioritized(to: T, dispose: Boolean): Self {
        // If dispose is false, `sender` wants to wait for the msg to be handled;
        // hence `state` is going to be tracked, and this `msg` is not going to be disposed by the receiver.
        if (dispose.not()) {
            sender = coroutineContext
            state.emit(Msg.State.PENDING)
        }

        to.priorityMsgChl.send(this)
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    override suspend fun reset(): Self {
        state.emit(Msg.State.UNTRACKED)
        sender = null
        @Suppress("UNCHECKED_CAST")
        return this as Self
    }

    /**
     * TODO
     */
    protected suspend fun handled() {
        assert(this !is ReqMsg<*, *, *>)

        // If the sender tracks msg state, then communicate that msg
        // was handled (msg is going to be disposed by the sender)
        if (state.value == Msg.State.PENDING) {
            state.emit(Msg.State.HANDLED)
        }
        // Else, after msg is handled, it can safely be disposed of.
        else dispose()
    }
}
