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

package org.opendc.simulator.network.utils.tracker

import java.util.TreeSet

internal interface TrackerMode<T : Trackable<T>> {
    val trackedProps: Set<TrackablePropId<T>>

    fun T.compare(other: T): Int

    fun T.shouldBeTracked(): Boolean

    fun setUp(items: Iterable<T>): TreeSet<T> {
        val treeSet =
            TreeSet<T> { a, b ->
                if (a === b) {
                    0
                } else {
                    a.compare(other = b).let { modeResult ->
                        if (modeResult == 0) {
                            (a.hashCode() - b.hashCode()).let { hashResult ->
                                if (hashResult == 0) {
                                    System.identityHashCode(a) - System.identityHashCode(b)
                                } else {
                                    hashResult
                                }
                            }
                        } else {
                            modeResult
                        }
                    }
                }
            }

        treeSet.addAll(items.filter { it.shouldBeTracked() })
        return treeSet
    }
}
