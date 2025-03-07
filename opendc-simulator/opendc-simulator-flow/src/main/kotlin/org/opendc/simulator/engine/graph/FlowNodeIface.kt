package org.opendc.simulator.engine.graph

import org.opendc.common.annotations.InternalUse
import org.opendc.common.annotations.PrivateSetter
import org.opendc.common.units.Unit
import org.opendc.simulator.engine.engine.FlowEngine
import org.opendc.simulator.engine.graph.FlowNode.NodeState
import java.time.InstantSource

/**
 * Allows [FlowNode] decorators.
 */
public interface FlowNodeIface {
    public val engine: FlowEngine
    public val clock: InstantSource

    @PrivateSetter
    public var nodeState: NodeState

    /**
     * The deadline of the stage after which an update should run.
     */
    @PrivateSetter
    public val deadline: Long

    /**
     * The index of the timer in the [FlowEventQueue].
     */
    @InternalUse
    public var timerIndex: Int
    @InternalUse
    public var inCycleQueue: Boolean

    /**
     * Invalidate the [FlowNode] forcing the stage to update.
     *
     *
     *
     * This method is similar to [.invalidate], but allows the user to manually pass the current timestamp to
     * prevent having to re-query the clock. This method should not be called during an update.
     */
    public fun invalidate(now: Long)

    /**
     * Invalidate the [FlowNode] forcing the stage to update.
     */
    public open fun invalidate() {
        invalidate(clock.millis())
    }

    /**
     * Update the state of the stage.
     */
    public fun update(now: Long)

    /**
     * This method is invoked when the one of the stage's InPorts or OutPorts is invalidated.
     *
     * @param now The virtual timestamp in milliseconds after epoch at which the update is occurring.
     * @return The next deadline for the stage.
     */
    public fun onUpdate(now: Long): Long

    /**
     * This method is invoked when an uncaught exception is caught by the engine. When this happens, the
     */
    public fun doFail(cause: Throwable?)

    /**
     * This method is invoked when the [FlowNode] exits successfully or due to failure.
     */
    public fun closeNode()

    public fun <T : Unit<T>> addDfltConsumerEdge(consumerEdge: FlowEdge<T>) {
        throw RuntimeException("No default `SupplyHandler` for the requested `Unit`")
    }

    public fun <T : Unit<T>> addDfltSupplierEdge(supplierEdge: FlowEdge<T>) {
        throw RuntimeException("No default `DemandHandler` for the requested `Unit`")
    }

    public fun <T : Unit<T>> rmDfltConsumerEdge(consumerEdge: FlowEdge<T>) {
        throw RuntimeException("No default `SupplyHandler` for the requested `Unit`")
    }

    public fun <T : Unit<T>> rmDfltSupplier(supplierEdge: FlowEdge<T>) {
        throw RuntimeException("No default `DemandHandler` for the requested `Unit`")
    }
}
