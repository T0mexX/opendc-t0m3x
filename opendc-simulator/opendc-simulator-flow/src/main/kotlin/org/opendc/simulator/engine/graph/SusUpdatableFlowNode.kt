package org.opendc.simulator.engine.graph

public suspend fun FlowNode.updateSusIfAble(now: Long) {
    if (this !is SusUpdatableFlowNode)  return this.update(now)
    if (this.nodeState == FlowNode.NodeState.CLOSED) {
        this.deadline = Long.MAX_VALUE
        return
    }

    this.nodeState = FlowNode.NodeState.UPDATING

    var newDeadline = this.deadline

    try {
        newDeadline = this.onUpdateSus(now)
    } catch (e: Exception) {
        doFailSus(e)
    }

    if (this.nodeState == FlowNode.NodeState.CLOSING) {
        closeNode()
        return
    }


    // Check whether the stage is marked as closing.
    if ((this.nodeState == FlowNode.NodeState.INVALIDATED) || (this.nodeState == FlowNode.NodeState.CLOSED)) {
        return
    }

    this.deadline = newDeadline


    // Update the timer queue with the new deadline
    engine.scheduleDelayedInContext(this)

    this.nodeState = FlowNode.NodeState.PENDING
}

public suspend fun FlowNode.closeNodeExt() {
    if (this is SusUpdatableFlowNode) closeNodeSus()
    else closeNode()
}

public interface SusUpdatableFlowNode {
    public suspend fun onUpdateSus(now: Long): Long

    public suspend fun doFailSus(cause: Throwable) {
        FlowNode.LOGGER.warn("Uncaught exception (closing stage)", cause)

        closeNodeSus()
    }

    public suspend fun closeNodeSus()
}
