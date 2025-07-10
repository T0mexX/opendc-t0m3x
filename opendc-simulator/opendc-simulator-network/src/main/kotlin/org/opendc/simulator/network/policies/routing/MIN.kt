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
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.internalstructs.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("min")
internal class MIN : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = ECMP()

    context(Node<*>)
    override suspend fun onNodeNewFReceived(nodeFEntry: NodeFlowEntry): Boolean {
        val f = nodeFEntry.f
        assert(nodeFEntry.txlinks.isEmpty())

        this@Node.routTbl.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .takeIf { it.isNotEmpty() }
            ?.first()
            ?.let { path ->
                nodeFEntry.txlinks.clear()
                nodeFEntry.txlinks[path.associatedLink()] = Percentage.ofPercentage(100)
            }

        return true
    }

    companion object {
        context(NetSimScope, Node<*>)
        fun selectPort(to: NodeId): Link =
            this@Node.routTbl.getPossiblePathsTo(to)
                .onlyMinimal()
                .first()
                .associatedLink()
    }
}

// /**
// * TODO
// */
// @Serializable
// @SerialName("ospf")
// internal data object OSPF: RoutPolicy {
//    context(NetSimScope, Node<*>)
//    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
//        val f = nodeFlowEntry.netFlow
//        this@Node.routTbl.getPossiblePathsTo(f.destId)
//            .onlyMinimal()
//            .firstOrNull()
//            ?.let { path ->
//                nodeFlowEntry.txlinks.clear()
//                nodeFlowEntry.txlinks += path.associatedPort()
//            }
//    }
//
//    context(NetSimScope, Node<*>)
//    suspend fun selectPorts(f: NetFlow): Set<Port> =
//        setOf(
//            this@Node.routTbl.getPossiblePathsTo(f.destId)
//                .onlyMinimal()
//                .random()
//                .associatedPort()
//        )
//
//    context(NetSimScope, Node<*>)
//    suspend fun selectPorts(to: Node<*>): Set<Port> =
//        setOf(
//            this@Node.routTbl.getPossiblePathsTo(to.id)
//                .onlyMinimal()
//                .random()
//                .associatedPort()
//        )
// }
