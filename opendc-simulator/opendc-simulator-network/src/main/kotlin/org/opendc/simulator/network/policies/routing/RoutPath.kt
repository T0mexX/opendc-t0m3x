package org.opendc.simulator.network.policies.routing

import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.NetFlow

/**
 * Represents specific routing path(s) for a flow in the network.
 *
 * @param f The flow this routing is applied to.
 * @param nextHops Maps each node to the ports they need
 * to forward flow [f) to, according to this [RoutPath].
 */
internal data class RoutPath(
    val f: INetFlow,
    private val nextHops: MutableMap<Node<*>, MutableSet<Port>>
) : MutableMap<Node<*>, MutableSet<Port>> by nextHops {
    companion object {
        suspend operator fun invoke(f: INetFlow, block: suspend MutableMap<Node<*>, MutableSet<Port>>.() -> Unit): RoutPath =
            RoutPath(
                f,
                buildMap {
                    block()
                }.toMutableMap()
            )
    }
}
