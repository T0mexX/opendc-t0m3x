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

    context(NetSimScope, Node<*>)
    override suspend fun onNodeNewFReceived(nodeFEntry: NodeFlowEntry): Boolean {
        val f = nodeFEntry.f
        val n = this@Node
        val meta = (n.routNodeMeta as MinRoutNodeMeta?) ?: attachMeta(n)
        nodeFEntry.txlinks.clear()

        n.routTbl.getPossiblePathsTo(f.destId)
            .onlyMinimal()
            .let { paths ->
                paths.elementAtOrNull(meta.rndmIdx % paths.size)
            }?.let { path ->
                nodeFEntry.txlinks[path.associatedLink()] = Percentage.ofPercentage(100)
            }

        return true
    }

    context(NetSimScope)
    private fun attachMeta(n: Node<*>): MinRoutNodeMeta {
        n.routNodeMeta = MinRoutNodeMeta(config.random.nextInt(from = 0, until = Int.MAX_VALUE))
        return n.routNodeMeta as MinRoutNodeMeta
    }

    /**
     * TODO: change docs
     * So that each node selects always the same path among those min available,
     * but that path does not depend on internal rout table implementation (e.g., with `first()`).
     */
    private inner class MinRoutNodeMeta(val rndmIdx: Int): RoutNodeMeta<MIN>

    companion object {
        context(NetSimScope, Node<*>)
        fun selectPort(to: NodeId): Link =
            this@Node.routTbl.getPossiblePathsTo(to)
                .onlyMinimal()
                .first()
                .associatedLink()
    }
}
