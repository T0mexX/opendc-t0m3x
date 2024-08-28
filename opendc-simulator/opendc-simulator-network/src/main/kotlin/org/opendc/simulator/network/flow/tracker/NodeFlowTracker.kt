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

package org.opendc.simulator.network.flow.tracker

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.tracker.TrackerMode.Companion.setUp
import org.opendc.simulator.network.utils.RWLock
import org.opendc.simulator.network.utils.logger
import java.util.TreeSet

/**
 * Keeps track of those flows whose demand is not satisfied,
 * maintaining multiple "lists" in which flows are ordered
 * according to some [TrackerMode]s in O(log(n)).
 *
 * The ordered lists can be retrieved for routing/fairness decisions.
 *
 * This class guarantees Thread safety regarding addition
 * and removal of both [TrackerMode]s and [OutFlow]s.
 */
internal class NodeFlowTracker(
    private val allOutgoingFlows: Map<FlowId, OutFlow>,
    vararg modes: TrackerMode,
) {
    private val treesByMode = mutableMapOf<TrackerMode, ModeTree>()
    private val treeLock = RWLock(readPermits = 10)

    init {
        modes.forEach { trackMode ->
            treesByMode.computeIfAbsent(trackMode) {
                ModeTree(trackMode.setUp(allOutgoingFlows))
            }
        }
    }

    suspend operator fun plus(mode: TrackerMode): Unit =
        treeLock.withWLock {
            treesByMode.computeIfAbsent(mode) { ModeTree(mode.setUp(allOutgoingFlows)) }
        }

    suspend operator fun minus(mode: TrackerMode) =
        treeLock.withWLock {
            treesByMode.remove(mode) ?: log.warn("unable to remove tracker mode $mode, mode not set")
        }

    /**
     * @return [List] that contains [OutFlow]s that are tracked
     * based on [mode], sorted by the comparator defined in [mode]
     */
    operator fun get(mode: TrackerMode): List<OutFlow> =
        runBlocking {
            this@NodeFlowTracker + mode
            treeLock.withRLock {
                treesByMode[mode]?.withTreeLock { it.toList() }!!
            }
        }

    suspend fun remove(outFlow: OutFlow) =
        treeLock.withRLock {
            treesByMode.values.forEach { modeTree ->
                modeTree.withTreeLock { it.remove(outFlow) }
            }
        }

    /**
     * @return the smaller [OutFlow] (based on the [mode]) among
     * those flows that are higher in the order than [outFlow] if it exists, else `null`.
     */
    fun nextHigherThan(
        outFlow: OutFlow,
        mode: TrackerMode,
    ): OutFlow? =
        runBlocking {
            treeLock.withRLock {
                this@NodeFlowTracker + mode
                treesByMode[mode]!!.withTreeLock { treeSet ->
                    var curr = outFlow
                    do {
                        curr = treeSet.higher(curr) ?: return@withTreeLock null
                    } while (curr.totRateOut.approxLarger(outFlow.totRateOut).not())

                    curr
                }
            }
        }

    /**
     * This method should be invoked every time [OutFlow.demand]
     * or [OutFlow.totRateOut] fields need to be updated.
     * The actual field update shall be executed in the [fieldChanger] block.
     *
     * This method keeps the sorted sets for each tracker mode updated, determining if an element
     * should be added (or its order updated) in the sortedSet.
     */
    fun OutFlow.handlePropChange(fieldChanger: () -> Unit) =
        runBlocking {
            treeLock.withRLock {
                rmIfNeeded()

                // Updates OutFlow field (either demand or totRateOut)
                fieldChanger()

                addIfNeeded()
            }
        }

    private suspend fun OutFlow.rmIfNeeded() =
        treeLock.withRLock {
            fun OutFlow.isNew(): Boolean = demand.isZero() && totRateOut.isZero()

            treesByMode.forEach { (mode, modeTree) ->
                modeTree.withTreeLock { tree ->
                    with(mode) {
                        // If condition is true, then element should be in the sortedSet and be removed.
                        // After this 'if' clause the OutFlow should never be in the tree.
                        // The removal of the flow has to be executed before field is updated.
                        if (shouldBeTracked()) {
                            tree.remove(this@rmIfNeeded).let {
                                if (isNew().not() && !it) {
                                    log.warn(
                                        "outflow ${this@rmIfNeeded} should have been in the sortedSet but wasn't",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    private suspend fun OutFlow.addIfNeeded() =
        treeLock.withRLock {
            treesByMode.forEach { (mode, modeTree) ->
                modeTree.withTreeLock { tree ->
                    with(mode) {
                        // If condition is true, then element should be added to the treeSet,
                        // since it is eligible for data rate increases.
                        if (shouldBeTracked()) {
                            tree.add(this@addIfNeeded)
                        }
                    }
                }
            }
        }

    private inner class ModeTree(private val tree: TreeSet<OutFlow>) {
        private val treeLock = Mutex()

        suspend fun <T> withTreeLock(block: (TreeSet<OutFlow>) -> T) =
            treeLock.withLock {
                block(tree)
            }
    }

    companion object {
        val log by logger()
    }
}
