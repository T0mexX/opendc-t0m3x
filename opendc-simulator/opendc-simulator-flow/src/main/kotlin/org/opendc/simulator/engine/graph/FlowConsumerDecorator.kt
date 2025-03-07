package org.opendc.simulator.engine.graph

import org.opendc.common.annotations.InternalUse
import org.opendc.common.annotations.PrivateSetter
import org.opendc.common.units.Unit
import org.opendc.simulator.engine.engine.FlowEngine
import java.time.InstantSource

/**
 *  Decorator-class for [FlowNode], that allows the node to act as a consumer of [T] [Unit].
 *
 *  With this decorator, a [FlowNode] can act as a consumer of multiple different [Unit]s,
 *  while maintaining type safety
 *
 * - Visibility is maintained as it is in [FlowNode] for all properties/methods,
 * even though it may be reduced for some.
 * - Documentation is maintained as it is in FlowNode even though some could be added.
 *
 * @param decorated The decorated [FlowNode].
 * @param handleSupply How a supply change of [T] [Unit] should be handled by the decorated node.
 */
@OptIn(PrivateSetter::class, InternalUse::class)
public class FlowConsumerDecorator<T: Unit<T>> (
    internal val decorated: FlowNode,
    internal val handleSupply: SupplyHndlr<T>
) : FlowNodeIface {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Decorator Methods/Properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override val clock: InstantSource
        get() = decorated.clock

    override val engine: FlowEngine
        get() = decorated.engine

    override var nodeState: FlowNode.NodeState
        get() = decorated.nodeState
        @PrivateSetter set(value) { decorated.nodeState = value }

    override var deadline: Long
        get() = decorated.deadline
        @PrivateSetter set(value) { decorated.deadline = value }

    @InternalUse
    override var inCycleQueue: Boolean
        get() = decorated.inCycleQueue
        set(value) { decorated.inCycleQueue = value }

    @InternalUse
    override var timerIndex: Int
        get() = decorated.timerIndex
        set(newIdx) { decorated.timerIndex = newIdx }

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

    public fun interface SupplyHndlr<T : Unit<T>> {
        public operator fun invoke(flowEdge: FlowEdge<T>, newSupply : T)
    }
}
