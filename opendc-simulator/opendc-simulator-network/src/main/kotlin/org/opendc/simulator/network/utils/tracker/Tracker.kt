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

import org.opendc.simulator.network.utils.IntSz
import java.util.TreeSet

/**
 * TODO
 */
internal interface Tracker<T : Trackable<T>> {
    var itemsGetter: () -> Iterable<T>

    infix operator fun plus(mode: TrackerMode<T>)

    infix operator fun minus(mode: TrackerMode<T>)

    /**
     * @return [List] that contains [T]s that are tracked
     * based on [mode], sorted by the comparator defined in [mode]
     */
    operator fun get(mode: TrackerMode<T>): Iterable<T>

    fun sizeOf(mode: TrackerMode<T>): IntSz

    fun remove(item: T)

    /**
     * This method should be invoked every time a field of [T] is updated.
     * The actual field update shall be executed in the [fieldChanger] block.
     *
     * This method keeps the sorted sets for each tracker mode updated, determining if an element
     * should be added (or its order updated) in the sortedSet.
     */
    context(T)
    fun handleFieldChange(
        propId: TrackablePropId<T>,
        fieldChanger: T.() -> Unit,
    )

    companion object {
        operator fun <T : Trackable<T>> invoke(
            vararg modes: TrackerMode<T>,
            itemsGetter: (() -> Iterable<T>)? = null,
        ) = object : Tracker<T> {
            private val treesByMode = mutableMapOf<TrackerMode<T>, TreeSet<T>>()
            override lateinit var itemsGetter: () -> Iterable<T>

            init {
                treesByMode.putAll(modes.associateWith { it.setUp(itemsGetter()) })
                itemsGetter?.let { this.itemsGetter = itemsGetter }
            }

            override operator fun plus(mode: TrackerMode<T>) {
                treesByMode.computeIfAbsent(mode) { mode.setUp(itemsGetter()) }
            }

            override infix operator fun minus(mode: TrackerMode<T>) {
                treesByMode.remove(mode)
            }

            override infix operator fun get(mode: TrackerMode<T>): Iterable<T> {
                this + mode
                return treesByMode[mode]!!
            }

            override fun sizeOf(mode: TrackerMode<T>): IntSz = (this[mode] as TreeSet<T>).size

            override fun remove(item: T) {
                treesByMode.values.forEach { treeSet ->
                    treeSet.remove(item)
                }
            }

            context(T)
            @Suppress("OVERRIDE_BY_INLINE")
            override inline fun handleFieldChange(
                propId: TrackablePropId<T>,
                fieldChanger: T.() -> Unit,
            ) {
                rmIfNeeded(propId)

                // Updates T field
                this@T.fieldChanger()

                addIfNeeded(propId)
            }

            context(T)
            private fun rmIfNeeded(propId: TrackablePropId<T>) {
                treesByMode.forEach { (mode, treeSet) ->
                    if (propId !in mode.trackedProps) return@forEach
                    with(mode) {
                        // If the condition is true, then an element should be in the sortedSet and be removed.
                        // After this 'if' clause, the OutFlow should never be in the tree.
                        // The removal of the flow has to be executed before the field is updated.
                        if (shouldBeTracked()) {
                            treeSet.remove(this@T)
                        }
                    }
                }
            }

            context(T)
            private fun addIfNeeded(propId: TrackablePropId<T>) {
                treesByMode.forEach { (mode, treeSet) ->
                    if (propId !in mode.trackedProps) return@forEach
                    with(mode) {
                        // If the condition is true, then the element should be added to the treeSet,
                        // since it is eligible for data rate increases.
                        if (shouldBeTracked()) {
                            treeSet.add(this@T)
                        }
                    }
                }
            }
        }
    }
}
