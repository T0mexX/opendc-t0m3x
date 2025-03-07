package org.opendc.simulator.engine.graph

import org.opendc.common.annotations.InternalUse
import org.opendc.common.annotations.PrivateSetter
import org.opendc.common.annotations.PrivateUse
import org.opendc.common.units.Unit


@Suppress("OVERRIDE_BY_INLINE")
@OptIn(PrivateSetter::class)
public class FlowSupplierDecorator<T: Unit<T>> (
    public val decorated: FlowNode,
    public val handleDemand: DemandHndlr<T>
) : FlowNode(decorated.graph) {

    public fun interface DemandHndlr<T : Unit<T>> {
        public operator fun invoke(flowEdge: FlowEdge<T>, newDemand : T)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Decorator Methods/Properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override var nodeState: NodeState
        inline get() = decorated.nodeState
        @PrivateSetter inline set(value) { decorated.nodeState = value }

    override var deadline: Long
        inline get() = decorated.deadline
        @PrivateSetter inline set(value) { decorated.deadline = value }

    @InternalUse
    override var inCycleQueue: Boolean
        inline get() = decorated.inCycleQueue
        inline set(value) { decorated.inCycleQueue = value }

    @InternalUse
    override var timerIndex: Int
        inline get() = decorated.timerIndex
        inline set(newIdx) { decorated.timerIndex = newIdx }

    override fun invalidate(now: Long) {
        decorated.invalidate(now)
    }

    override fun invalidate() {
        invalidate(clock.millis())
    }

    override fun update(now: Long) {
        decorated.update(now)
    }

    override fun onUpdate(now: Long): Long = decorated.onUpdate(now)

    override fun doFail(cause: Throwable?) {
        decorated.doFail(cause)
    }

    override fun closeNode() {
        decorated.closeNode()
    }
}
