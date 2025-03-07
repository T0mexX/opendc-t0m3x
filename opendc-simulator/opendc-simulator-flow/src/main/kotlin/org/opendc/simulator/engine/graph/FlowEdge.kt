package org.opendc.simulator.engine.graph

import org.opendc.common.units.Unit
import org.opendc.common.units.UnitType

public class FlowEdge<T: Unit<T>> @JvmOverloads constructor(
    public val unitType: UnitType<T>,
    public var consumerIndex: Int = -1,
    public var supplierIndex: Int = -1,
) {
    private lateinit var consumerDec: FlowConsumerDecorator<T>
    private lateinit var supplierDec: FlowSupplierDecorator<T>
    public val consumer: FlowNode
        get() = consumerDec.decorated
    public val supplier: FlowNode
        get() = supplierDec.decorated

    public var demand: T = unitType.zero
        private set
    public var supply: T = unitType.zero
        private set
    public var capacity: T = unitType.max

    public fun setConsumer(flowNode: FlowNode, supplyHandler: FlowConsumerDecorator.SupplyHndlr<T>) {
        consumerDec = FlowConsumerDecorator(flowNode, supplyHandler)
    }

    public fun setSupplier(flowNode: FlowNode, demandHandler: FlowSupplierDecorator.DemandHndlr<T>) {
        supplierDec = FlowSupplierDecorator(flowNode, demandHandler)
    }

    public fun close() {
        TODO()
    }

    @JvmSynthetic
    public fun pushDemand(newDemand: T, forceThrough: Boolean = false) {
        if (this.demand == newDemand && !forceThrough) return
        demand = newDemand
        supplierDec.handleDemand(this, newDemand)
    }

    @JvmSynthetic
    public fun pushSupply(newSupply: T, forceThrough: Boolean = false) {
        if (this.demand == newSupply && !forceThrough) return
        this.supply = newSupply
        consumerDec.handleSupply(this, newSupply)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // To be removed after every FlowNode subclass is converted to kotlin
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @OptIn(Unit.UnsafeUnitOperation::class)
    public fun setSupplier(flowNode: FlowNode, demandHandler: (FlowEdge<T>, Double) -> kotlin.Unit) {
        supplierDec = FlowSupplierDecorator(flowNode) { edge, unit ->
            demandHandler(
                edge,
                unit.toBase()
            )
        }
    }

    @OptIn(Unit.UnsafeUnitOperation::class)
    public fun setConsumer(flowNode: FlowNode, supplyHandler: (FlowEdge<T>, Double) -> kotlin.Unit) {
        supplierDec = FlowSupplierDecorator(flowNode) { edge, unit ->
            supplyHandler(
                edge,
                unit.toBase()
            )
        }
    }
}
