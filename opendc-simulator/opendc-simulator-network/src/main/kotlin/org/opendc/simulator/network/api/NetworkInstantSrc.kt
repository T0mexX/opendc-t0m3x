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

package org.opendc.simulator.network.api

import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetworkController.Companion.log
import java.time.Instant
import java.time.InstantSource

/**
 * Represent the time source of the network.
 * The time source can either be internal (can be advanced with [advanceTime])
 * or external (the current time/instant can be retrieved with [time]/[instant] and is updated externally).
 */
internal class NetworkInstantSrc private constructor(
    private val external: InstantSource? = null,
    internal: Time = Time.ZERO,
) : InstantSource {
    constructor(external: InstantSource? = null) : this(external = external, internal = Time.ZERO)

    constructor(internal: Time = Time.ZERO) : this(external = null, internal = internal)

    var internal = internal
        private set
    val isExternalSource: Boolean = external != null
    val isInternalSource: Boolean = isExternalSource.not()

    val time: Time get() = Time.ofInstantFromEpoch(instant())

    override fun instant(): Instant =
        external?.instant()
            ?: Instant.ofEpochMilli(internal.toMsLong())

    /**
     * Advances the internal time by [time].
     */
    fun advanceTime(time: Time) {
        external?.let { return log.error("unable to advance internal time, network has external time source") }
        internal += time
    }

    /**
     * Sets internal time to [time].
     * @throws[IllegalArgumentException] if the current time source is external.
     */
    fun setInternalTime(time: Time) {
        require(isInternalSource)
        internal = time
    }
}
