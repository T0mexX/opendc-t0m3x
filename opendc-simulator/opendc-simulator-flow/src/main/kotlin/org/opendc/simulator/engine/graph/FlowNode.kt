package org.opendc.simulator.engine.graph

import org.opendc.common.annotations.InternalUse
import org.opendc.common.annotations.PrivateSetter
import org.opendc.simulator.engine.engine.FlowEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.InstantSource

// TODO: When all classes in the flow module are converted to kotlin,
//  some public member can be converted to internal
/**
 * A [FlowNode] represents a node in a [FlowGraph].
 */
@OptIn(PrivateSetter::class)
public abstract class FlowNode(internal val graph: FlowGraph): FlowNodeIface {
    final override val engine: FlowEngine = graph.engine
    public final override val clock: InstantSource = engine.clock

    @PrivateSetter
    override var nodeState: NodeState = NodeState.PENDING

    /**
     * The deadline of the stage after which an update should run.
     */
    @PrivateSetter
    override var deadline: Long = Long.MAX_VALUE

    /**
     * The index of the timer in the [FlowEventQueue].
     */
    @InternalUse
    override var timerIndex: Int = -1

    @InternalUse
    override var inCycleQueue: Boolean = false

    init {
        @Suppress("LeakingThis")
        graph.addNode(this)
    }

    public enum class NodeState {
        PENDING,  // Stage is active, but is not running any updates
        UPDATING,  // Stage is active, and running an update
        INVALIDATED,  // Stage is deemed invalid, and should run an update
        CLOSING,  // Stage is being closed, final updates can still be run
        CLOSED // Stage is closed and should not run any updates
    }

    /**
     * Invalidate the [FlowNode] forcing the stage to update.
     *
     *
     *
     * This method is similar to [.invalidate], but allows the user to manually pass the current timestamp to
     * prevent having to re-query the clock. This method should not be called during an update.
     */
    public override fun invalidate(now: Long) {
        // If there is already an update running,
        // notify the update that the next update should be run after

        if (this.nodeState != NodeState.CLOSING && this.nodeState != NodeState.CLOSED) {
            this.nodeState = NodeState.INVALIDATED
            engine.scheduleImmediate(now, this)
        }
    }

    /**
     * Invalidate the [FlowNode] forcing the stage to update.
     */
    public override fun invalidate() {
        invalidate(clock.millis())
    }

    /**
     * Update the state of the stage.
     */
    public override fun update(now: Long) {
        if (this.nodeState == NodeState.CLOSED) {
            this.deadline = Long.MAX_VALUE
            return
        }

        this.nodeState = NodeState.UPDATING

        var newDeadline = this.deadline

        try {
            newDeadline = this.onUpdate(now)
        } catch (e: Exception) {
            doFail(e)
        }

        if (this.nodeState == NodeState.CLOSING) {
            closeNode()
            return
        }

        // Check whether the stage is marked as closing.
        if ((this.nodeState == NodeState.INVALIDATED) || (this.nodeState == NodeState.CLOSED)) {
            return
        }

        this.deadline = newDeadline

        // Update the timer queue with the new deadline
        engine.scheduleDelayedInContext(this)

        this.nodeState = NodeState.PENDING
    }

    /**
     * This method is invoked when the one of the stage's InPorts or OutPorts is invalidated.
     *
     * @param now The virtual timestamp in milliseconds after epoch at which the update is occurring.
     * @return The next deadline for the stage.
     */
    public abstract override fun onUpdate(now: Long): Long

    /**
     * This method is invoked when an uncaught exception is caught by the engine. When this happens, the
     */
    public override fun doFail(cause: Throwable?) {
        LOGGER.warn("Uncaught exception (closing stage)", cause)

        closeNode()
    }

    /**
     * This method is invoked when the [FlowNode] exits successfully or due to failure.
     */
    public override fun closeNode() {
        if (this.nodeState == NodeState.CLOSED) {
            return
        }

        // Mark the stage as closed
        this.nodeState = NodeState.CLOSED

        // Remove stage from parent graph
        graph.removeNode(this)

        // Remove stage from the timer queue
        this.deadline = Long.MAX_VALUE
        engine.scheduleDelayedInContext(this)
    }

    internal companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(FlowNode::class.java)
    }
}
