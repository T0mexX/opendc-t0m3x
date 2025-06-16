/*
 * Copyright (c) 2025 AtLarge Research
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

package org.opendc.simulator.network.simscope

import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.common.units.Timestamp.Companion.toTimestamp
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import java.time.Instant
import java.time.InstantSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal sealed class NetSimTmSrc<Self : NetSimTmSrc<Self>> : InstantSource, AbstractCoroutineContextElement(Key) {
    abstract val initialTmStamp: Timestamp

    /**
     * TODO
     */
    abstract val tmstamp: Timestamp

    /**
     * TODO
     */
    val sinceStart: TimeDelta get() = tmstamp timeDelta initialTmStamp

    /**
     * TODO
     */
    fun isSync(tmstamp: Timestamp): Boolean = tmstamp approx this.tmstamp

    /**
     * TODO
     */
    class Internal(override val initialTmStamp: Timestamp = Timestamp.ofEpochMs(0)) : NetSimTmSrc<Internal>() {
        override var tmstamp: Timestamp = initialTmStamp

        override fun instant(): Instant = tmstamp.toInstant()

        /**
         * TODO
         */
        context(NetSimScope)
        suspend fun advanceBy(
            tmDelta: TimeDelta,
            stabMode: NetSimStabilityMode = this@NetSimScope.config.stabilityMode,
        ) = barrier.whileStable(stabMode) {
            require(tmDelta >= TimeDelta.zero)
            tmstamp += tmDelta
        }
    }

    /**
     * TODO
     * if new fetched erlier than last then error
     */
    class External(private var instantSrc: InstantSource) : NetSimTmSrc<External>() {
        override val initialTmStamp: Timestamp = Timestamp.ofInstant(instantSrc.instant())

        /**
         * TODO
         */
        private var lastFetched: Instant = instantSrc.instant()

        override val tmstamp: Timestamp get() =
            instantSrc.instant().let {
                check(it >= lastFetched)
                lastFetched = it
                it.toTimestamp()
            }

        override fun instant(): Instant =
            instantSrc.instant().also {
                check(it >= lastFetched)
                lastFetched = it
            }
    }

    companion object Key : CoroutineContext.Key<NetSimTmSrc<*>>
}
