package org.opendc.simulator.network.utils.tracker

import org.opendc.common.logger.logger
import java.util.TreeSet


internal class Tracker<T>(
    vararg modes: TrackerMode<T>,
    private val items: Collection<T>,
) {
    private val treesByMode = mutableMapOf<TrackerMode<T>, TreeSet<T>>()

    init {
        treesByMode.putAll(modes.associateWith { it.setUp(items) })
    }

    operator fun plus(mode: TrackerMode<T>) {
        treesByMode.putIfAbsent(mode, mode.setUp(items))
    }

    operator fun minus(mode: TrackerMode<T>) {
        treesByMode.remove(mode) ?: log.warn("unable to remove tracker mode $mode, mode not set")
    }

    /**
     * @return [List] that contains [T]s that are tracked
     * based on [mode], sorted by the comparator defined in [mode]
     */
    operator fun get(mode: TrackerMode<T>): Iterable<T> {
        this + mode
        return treesByMode[mode]!!
    }

    private fun remove(item: T) {
        treesByMode.values.forEach { treeSet ->
            treeSet.remove(item)
        }
    }

    /**
     * This method should be invoked every time a field of [T] is updated.
     * The actual field update shall be executed in the [fieldChanger] block.
     *
     * This method keeps the sorted sets for each tracker mode updated, determining if an element
     * should be added (or its order updated) in the sortedSet.
     */
    context(T)
    inline fun handleFieldChange(fieldChanger: T.() -> Unit) {
        rmIfNeeded()

        // Updates T field
        this@T.fieldChanger()

        addIfNeeded()
    }

    context(T)
    private fun rmIfNeeded() {
        treesByMode.forEach { (mode, treeSet) ->
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
    private fun addIfNeeded() {
        treesByMode.forEach { (mode, treeSet) ->
            with(mode) {
                // If the condition is true, then the element should be added to the treeSet,
                // since it is eligible for data rate increases.
                if (shouldBeTracked()) {
                    treeSet.add(this@T)
                }
            }
        }
    }

    companion object {
        val log by logger()
    }
}
