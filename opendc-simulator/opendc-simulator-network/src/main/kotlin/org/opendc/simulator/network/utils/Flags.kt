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

@file:OptIn(InternalUse::class)

package org.opendc.simulator.network.utils

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

public interface Flags<T> {
    @InternalUse
    public val value: Int

    public fun <P> ifSet(
        flag: Flag<T>,
        dflt: P? = null,
        block: () -> P,
    ): P? =
        if (this.value and flag.value != 0) {
            block()
        } else {
            dflt
        }

    public operator fun plus(other: Flags<T>): Flags<T> = MultiFlags(this.value or other.value)

    public operator fun minus(other: Flags<T>): Flags<T> = MultiFlags(this.value and other.value.inv())

    public companion object {
        public fun <T> all(): Flags<T> = MultiFlags(-1)
    }
}

@JvmInline
private value class MultiFlags<T>(
    override val value: Int,
) : Flags<T>

@JvmInline
public value class Flag<T> private constructor(
    override val value: Int,
) : Flags<T> {
    init {
        // Check power of 2 => 1 bit set.
        require(value != 0 && (value and (value - 1)) == 0)
    }

    public companion object {
        /**
         * Keeps track of the bits already used for each flag type.
         */
        private val flagsCounters = mutableMapOf<KClass<*>, Int>()
        private val countersLock = Mutex()

        /**
         * Constructor creating a new flag for type [T] if a bit is available else throws.
         * @throws[RuntimeException] if all bits for flags of type [T] are already in use.
         */
        internal inline operator fun <reified T> invoke(): Flag<T> =
            runBlocking {
                countersLock.withLock {
                    val shift: Int =
                        flagsCounters.compute(T::class) { _, counter ->
                            counter?.plus(1) ?: 0
                        }!!

                    if (shift >= Int.SIZE_BITS) {
                        throw RuntimeException("max number of flags for type ${T::class.simpleName} reached (${Int.SIZE_BITS})")
                    } else {
                        Flag(1 shl shift)
                    }
                }
            }
    }
}
