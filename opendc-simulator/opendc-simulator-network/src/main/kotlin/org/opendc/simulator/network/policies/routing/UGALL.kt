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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internalstructs.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.max

/**
 * TODO
 *
 * Approximation, load based routing is recomputed only when new flow arrives at node,
 * not for each single packet.
 *
 * The tx [Port] is selected considering both the path length and the congestion at the port.
 */
@Serializable
@SerialName("ugall")
internal class UGALL : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = this

    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFEntry: NodeFlowEntry) {
        val f = nodeFEntry.netFlow
        nodeFEntry.txlinks.clear()

        // Ports the flow will be forwarded to.
        val paths = this@Node.routTbl.getPossiblePathsTo(f.destId)

        assert(paths.map { it.nextHop }.toSet().size == paths.size)

        var scoreSum = .0
        paths.map { p ->
            val port = p.associatedLink()
            val availableBw = port.availableBw

            port to max(10 - p.distance, 1) *
                (availableBw max (port.maxBw / 100)).tobps().also {
                    scoreSum += it
                }
        }.forEach { (port, score) ->
            val prop =
                if (scoreSum == .0) {
                    Percentage.zero
                } else {
                    Percentage.ofRatio(score / scoreSum)
                }
            if (prop.isZero().not()) nodeFEntry.txlinks[port] = prop
        }

//        // Choose path considering both path length and congestion at the port.
//        val chosenPath = paths.minBy { path ->
//            // Port to send to if this path is used.
//            val port = path.associatedPort()
//
//            // Score for the port, less is better.
//            path.distance * port.txLink!!.util.toRatio()
//            path.distance * (nodeFlowEntry.rx - port.txLink!!.availableBw).tobps()
//        }
//
//        // Set the chosen port as the only one that will handle the outgoing flow.
//        nodeFlowEntry.txlinks[chosenPath.associatedPort()] = Percentage.ofPercentage(100)
    }
}
