package org.opendc.simulator.network.utils.invalidatable.internals

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal open class InvalidatorChl<T> private constructor(
    private val delegatedChl: Channel<T>,
    private val receiver: Invalidatable
): Channel<T> by delegatedChl {

    constructor(receiver: Invalidatable) :
        this(delegatedChl = Channel<T>(Channel.UNLIMITED), receiver = receiver)

    /**
     * Number of pending updates to be collected from the channel.
     */
    var pending: Int = 1
    private val pendingMtx = Mutex()

    /**
     * Suspending implementation of [tryReceive]
     * in order to invalidate [receiver].
     */
    suspend fun tryReceiveValidate(): ChannelResult<T> {
        val res = delegatedChl.tryReceive()
        if (res.isSuccess) {
            pendingMtx.withLock {
                pending--
            }
            (res.getOrThrow() as? Invalidatable)?.validate()
        }
        return res
    }

    @Deprecated(
        message = "This method must not be called",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("tryReceiveValidate()")
    )
    override fun tryReceive(): ChannelResult<T> = throw UnsupportedOperationException()

    suspend fun bo() {
        select<Unit> {
            delegatedChl.onReceive {

            }

        }
    }

    /**
     * This override also works when [onReceive] is used, since [onReceive]
     * is called only once when the method will succeed.
     */
    override suspend fun receive(): T {
        pendingMtx.withLock {
            if (--pending == 0) receiver.validate()
        }

        return delegatedChl.receive().also {
            if (it is Invalidatable) it.validate()
        }
    }

    override suspend fun send(element: T) {
        pendingMtx.withLock {
            if (++pending == 1) receiver.invalidate()
        }

        delegatedChl.send(element)
    }
}
