package org.opendc.simulator.network.policies.fairness

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.utils.NonSerializable
import kotlin.coroutines.AbstractCoroutineContextElement
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
@Polymorphic
internal sealed class FairnessPolicy: AbstractCoroutineContextElement(Key) {
    /**
     * Determines the various flows bandwidth at a port whenever
     * the sum of their demand exceeds the port/link bandwidth.
     *
     * It sets up the [PortFlowEntry.demand].
     * @param entryList The list of flow entries at a specific port.
     */
    context(Port)
    abstract suspend fun applyFairness(entryList: List<PortFlowEntry>)

    /**
     * Usually, processing demand reductions is the first step in applying fairness,
     * since freeing some bandwidth can allow other currently unsatisfied flows,
     * to have their throughput increased.
     */
    context(Port)
    suspend fun processDemandReductions(entryList: List<PortFlowEntry>) = coroutineScope {
        entryList.asFlow().onEach { entry ->
            if (entry.used.not()) return@onEach
            if (entry.demand < entry.tput) {
                this@Port.txLink!!.releaseBw(entry.tput - entry.demand, entry.netF)
                entry.tput = entry.demand
            }
        }.launchIn(this@coroutineScope)
    }

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
