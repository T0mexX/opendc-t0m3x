package org.opendc.simulator.network.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal class CoroutineID private constructor(
    internal val value: Int
): AbstractCoroutineContextElement(Key) {


    companion object Key : CoroutineContext.Key<CoroutineID> {
        private var nextId: Int = 0
        private val mtx = Mutex()

        /**
         * TODO
         */
        internal suspend fun new(): CoroutineID = mtx.withLock {
            CoroutineID(nextId++)
        }
    }
}
