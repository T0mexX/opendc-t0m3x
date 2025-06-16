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

package org.opendc.simulator.network.policies.routing

import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node

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
    private val nextHops: MutableMap<Node<*>, MutableMap<Link, Percentage>>,
) : MutableMap<Node<*>, MutableMap<Link, Percentage>> by nextHops {
    companion object {
        suspend operator fun invoke(
            f: INetFlow,
            block: suspend MutableMap<Node<*>, MutableMap<Link, Percentage>>.() -> Unit,
        ): RoutPath =
            RoutPath(
                f,
                buildMap {
                    block()
                }.toMutableMap(),
            )
    }
}
