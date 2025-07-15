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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    context(NetSimScope)
    abstract suspend fun <T> whileFrozen(assertFrozenAt: Timestamp? = null, block: suspend () -> T): T

    /**
     * TODO
     */
    class Internal(override val initialTmStamp: Timestamp = Timestamp.ofEpochMs(0)) : NetSimTmSrc<Internal>() {
        override var tmstamp: Timestamp = initialTmStamp

        override fun instant(): Instant = tmstamp.toInstant()

        context(NetSimScope)
        override suspend fun <T> whileFrozen(assertFrozenAt: Timestamp?, block: suspend () -> T, ): T =
            TODO("Shouldn't be needed for now")

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
        private val frozenMtx = Mutex()
        private var frozenTmStamp: Timestamp? = null

        context(NetSimScope)
        override suspend fun <T> whileFrozen(assertFrozenAt: Timestamp?, block: suspend () -> T): T =
            barrier.whileStable(netSimStabilityMode = NetSimStabilityMode.ENFORCED) {
                frozenTmStamp = tmstamp
                check(assertFrozenAt == null || assertFrozenAt == frozenTmStamp) {
                    "Network simulation time source frozen too late"
                }
                block().also { frozenTmStamp = null }
            }


        /**
         * TODO
         * Different
         */
        private var lastFetched: Timestamp = instantSrc.instant().toTimestamp()

        override val tmstamp: Timestamp get() =
            frozenTmStamp ?: instantSrc.instant().toTimestamp().also {
                check(it >= lastFetched) { "lastFetched=$lastFetched, curr=$it" }
                lastFetched = it
            }

        override fun instant(): Instant =
            frozenTmStamp?.toInstant() ?: instantSrc.instant().also {
                val asTs = it.toTimestamp()
                check(asTs >= lastFetched) { "lastFetched=$lastFetched, curr=${asTs}" }
                lastFetched = asTs
            }
    }

    companion object Key : CoroutineContext.Key<NetSimTmSrc<*>>
}
