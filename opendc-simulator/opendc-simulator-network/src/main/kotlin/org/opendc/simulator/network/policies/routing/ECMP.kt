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

/**
 * TODO
 */
@Serializable
@SerialName("ecmp")
internal class ECMP : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = this

    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFEntry: NodeFlowEntry) {
        val f = nodeFEntry.netFlow
        nodeFEntry.txlinks.clear()

        // Ports the flow will be forwarded to.
        val txPorts = this@Node.routTbl.getPossiblePathsTo(f.destId).onlyMinimal()

        // Add the tx ports to the `NodeFlowEntry`, with the corresponding
        // percentage of this flow's data forwarded to those ports.
        // Each port is assigned an equal share of the total data to send
        txPorts.forEach {
            nodeFEntry.txlinks[it.associatedLink()] = Percentage.ofRatio(1.0 / txPorts.size)
        }
    }
}
