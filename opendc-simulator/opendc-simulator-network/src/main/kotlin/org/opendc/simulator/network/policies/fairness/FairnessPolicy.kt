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

package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.link.LinkEntry
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
@Polymorphic
internal sealed class FairnessPolicy : AbstractCoroutineContextElement(Key) {
    /**
     * Determines the various flows bandwidth at a port whenever
     * the sum of their demand exceeds the port/link bandwidth.
     *
     * It sets up the [LinkEntry.demand].
     * @param entryList The list of flow entries at a specific port.
     */
    context(Link)
    abstract suspend fun applyFairness(entryList: List<LinkEntry>)

    /**
     * Usually, processing demand reductions is the first step in applying fairness,
     * since freeing some bandwidth can allow other currently unsatisfied flows,
     * to have their throughput increased.
     */
    context(Link)
    suspend fun processDemandReductions(entryList: List<LinkEntry>) {
        TODO()
    }
//        entryList.asFlow().onEach { entry ->
//            if (entry.used.not()) return@onEach
//            if (entry.demand < entry.tput) {
//                this@Port.txLink!!.releaseBw(entry.tput - entry.demand, entry.netF)
//                entry.tput = entry.demand
//            }
//        }.launchIn(this@coroutineScope)
//    }

    /**
     * Invoked after network construction.
     */
    context(NetSimScope)
    internal open suspend fun setUp() { /* NoOps */ }

    /**
     * It performs necessary actions so that the flow is correctly routed through the network.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStart(f: INetFlow) { /* NoOps */ }

    /**
     * It performs necessary actions so that all resources associated with the flow routing are freed.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onFlowStop(f: INetFlow) { /* NoOps */ }

    /**
     * It performs the necessary updates due to node addition.
     *
     * This method needs to be invoked by the [NetSimScope] main job.
     */
    context(NetSimScope)
    internal open suspend fun onNodeAdded(n: Node<*>) { /* NoOps */ }

    /**
     * It performs necessary actions so that flows that where
     * routed through the removed node are rerouted correctly.
     */
    context(NetSimScope)
    internal open suspend fun onNodeRemoved(n: Node<*>) { /* NoOps */ }

    companion object Key : CoroutineContext.Key<FairnessPolicy>
}
