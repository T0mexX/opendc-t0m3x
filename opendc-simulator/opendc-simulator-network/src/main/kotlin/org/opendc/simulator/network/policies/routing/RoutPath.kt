package org.opendc.simulator.network.policies.routing

import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow

/**
 * Represents specific routing path(s) for a flow in the network.
 *
 * @param f The flow this routing is applied to.
 * @param nextHops Maps each node to the ports they need
 * to forward flow [f) to, according to this [RoutPath].
 */
internal data class RoutPath(
    private val f: NetFlow,
    private val nextHops: Map<Node<*>, Set<Port>>
) : Map<Node<*>, Set<Port>> by nextHops {
    companion object {
        suspend operator fun invoke(f: NetFlow, block: suspend MutableMap<Node<*>, Set<Port>>.() -> Unit): RoutPath =
            RoutPath(
                f,
                buildMap {
                    block()
                }
            )
    }
}
