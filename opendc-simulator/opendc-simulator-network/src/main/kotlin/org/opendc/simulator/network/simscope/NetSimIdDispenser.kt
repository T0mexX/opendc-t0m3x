package org.opendc.simulator.network.simscope

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.flow.FlowId2
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class NetSimIdDispenser: AbstractCoroutineContextElement(Key) {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NodeId Dispensing
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var nextNodeId = NodeId2(0)
    private val nodeIdMtx = Mutex()
    private val claimedNodeIds = mutableSetOf<NodeId2>()

    suspend fun getNodeId(): NodeId2 = nodeIdMtx.withLock {
        while (nextNodeId in claimedNodeIds) claimedNodeIds.remove(nextNodeId)

        return nextNodeId++
    }

    suspend fun claimNodeId(id: NodeId2): NodeId2? = nodeIdMtx.withLock {
        if (id < nextNodeId || id in claimedNodeIds) null
        else id
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowId Dispensing
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var nextFlowId = FlowId2(0)
    private val flowIdMtx = Mutex()
    private val claimedFlowIds = mutableSetOf<FlowId2>()

    suspend fun getFlowId(): FlowId2 = nodeIdMtx.withLock {
        while (nextFlowId in claimedFlowIds) claimedFlowIds.remove(nextFlowId)

        return nextFlowId++
    }

    suspend fun claimFlowId(id: FlowId2): FlowId2? = nodeIdMtx.withLock {
        if (id < nextFlowId || id in claimedFlowIds) null
        else id
    }

    companion object Key : CoroutineContext.Key<NetSimIdDispenser>
}
