package org.opendc.simulator.network.utils.tracker

import java.util.TreeSet

internal interface TrackerMode<T> {
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

        treeSet.addAll(items)
        return treeSet
    }
}
