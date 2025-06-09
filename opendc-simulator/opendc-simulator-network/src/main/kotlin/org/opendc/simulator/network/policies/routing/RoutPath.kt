package org.opendc.simulator.network.policies.routing

import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.internals.INetFlow

/**
 * Represents specific routing path(s) for a flow in the network.
 *
 * @param f The flow this routing is applied to.
 * @param nextHops Maps each node to the ports they need
 * to forward flow [f] to and the percentage of the data sent to each port,
 * according to this [RoutPath].
 */
internal data class RoutPath(
    val f: INetFlow,
    private val nextHops: MutableMap<Node<*>, MutableMap<Link, Percentage>>
) : MutableMap<Node<*>, MutableMap<Link, Percentage>> by nextHops {
    companion object {
        suspend operator fun invoke(
            f: INetFlow,
            block: suspend MutableMap<Node<*>, MutableMap<Link, Percentage>>.() -> Unit
        ): RoutPath =
            RoutPath(
                f,
                buildMap {
                    block()
                }.toMutableMap()
            )
    }
}
