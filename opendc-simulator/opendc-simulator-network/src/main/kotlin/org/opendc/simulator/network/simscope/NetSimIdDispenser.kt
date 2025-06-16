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

package org.opendc.simulator.network.simscope

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.components.node.NodeId
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class NetSimIdDispenser : AbstractCoroutineContextElement(Key) {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NodeId Dispensing
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var nextNodeId = NodeId(0)
    private val nodeIdMtx = Mutex()
    private val claimedNodeIds = mutableSetOf<NodeId>()

    suspend fun getNodeId(): NodeId =
        nodeIdMtx.withLock {
            while (nextNodeId in claimedNodeIds) claimedNodeIds.remove(nextNodeId)

            return nextNodeId++
        }

    suspend fun claimNodeId(id: NodeId): NodeId? =
        nodeIdMtx.withLock {
            if (id < nextNodeId || id in claimedNodeIds) {
                null
            } else {
                id
            }
        }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowId Dispensing
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var nextFlowId = FlowId(0)
    private val flowIdMtx = Mutex()
    private val claimedFlowIds = mutableSetOf<FlowId>()

    suspend fun getFlowId(): FlowId =
        nodeIdMtx.withLock {
            while (nextFlowId in claimedFlowIds) claimedFlowIds.remove(nextFlowId)

            return nextFlowId++
        }

    suspend fun claimFlowId(id: FlowId): FlowId? =
        nodeIdMtx.withLock {
            if (id < nextFlowId || id in claimedFlowIds) {
                null
            } else {
                id
            }
        }

    companion object Key : CoroutineContext.Key<NetSimIdDispenser>
}
