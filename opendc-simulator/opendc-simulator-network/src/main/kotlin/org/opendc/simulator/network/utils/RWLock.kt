/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore

/**
 * A read-write suspend lock implementation using a [Semaphore].
 * When someone is attempting to lock the resource for write, all
 * other attempts (read or write) are negated until the resource is
 * acquired for write (all semaphore permits are acquired by the same entity).
 */
internal class RWLock(private val readPermits: Int = 5) {
    /**
     * Semaphore used internally for the lock implementation.
     * A write lock is acquired when all semaphore permits are acquired by the same caller.
     * A lock is acquired by just acquiring one permit of the semaphore.
     */
    private val sem = Semaphore(permits = readPermits)

    /**
     * When this mutex is locked, any attempt to lock the resource
     * (read or write) will be negated until the one that locked
     * this mutex acquires the resource for write.
     */
    private var attemptingWMutex = Mutex()

    // lockRead and unlockRead not provided since it would allow
    // unlocking without locking, and preventing it would decrease performance
    // use withReadLock instead

    private suspend fun lockWrite(owner: Any? = null) {
        attemptingWMutex.lock(owner)
        repeat(readPermits) { sem.acquire() }
        return
    }

    private fun unlockWrite(owner: Any? = null) {
        attemptingWMutex.unlock(owner)
        repeat(readPermits) { sem.release() }
        return
    }

    suspend fun <T> withRLock(block: suspend () -> T): T {
        sem.acquire()
        val result: T = block()
        sem.release()
        return result
    }

    suspend fun <T> withWLock(block: suspend () -> T): T {
        lockWrite()
        val result: T = block()
        unlockWrite()
        return result
    }

    fun <T> tryWithRLock(block: () -> T): T? {
        return if (sem.tryAcquire()) {
            val result = block()
            sem.release()
            result
        } else {
            null
        }
    }
}
