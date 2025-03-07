package org.opendc.simulator.engine.graph

import org.opendc.common.units.Unit
import org.opendc.common.units.UnitType
import java.util.Arrays


public class FlowDistributor <T: Unit<T>>(
    graph: FlowGraph,
    private val unitType: UnitType<T>,
) : FlowNode(graph) {
    private val consumerEdges = ArrayList<FlowEdge<T>>()
    private lateinit var supplierEdge: FlowEdge<T>

    private val incomingDemands = ArrayList<T>() // What is demanded by the consumers
    private val outgoingSupplies = ArrayList<T>() // What is supplied to the consumers

    private var totalIncomingDemand: T = unitType.zero // The total demand of all the consumers
    private var currentIncomingSupply: T = unitType.zero // The current supply provided by the supplier

    private var outgoingDemandUpdateNeeded = false
    private var updatedDemands: MutableSet<Int> =
        HashSet() // Array of consumers that updated their demand in this cycle

    private var overloaded = false

    public var capacity: T = unitType.zero // What is the max capacity. Can probably be removed
        private set

    public override fun onUpdate(now: Long): Long {
        // Check if current supply is different from total demand

        if (this.outgoingDemandUpdateNeeded) {
            this.updateOutgoingDemand()

            return Long.MAX_VALUE
        }

        if (outgoingSupplies.isNotEmpty()) {
            this.updateOutgoingSupplies()
        }

        return Long.MAX_VALUE
    }

    private fun updateOutgoingDemand() {
        supplierEdge.pushDemand(totalIncomingDemand)

        this.outgoingDemandUpdateNeeded = false

        this.invalidate()
    }

    @OptIn(Unit.UnsafeUnitOperation::class)
    private fun updateOutgoingSupplies() {
        // If the demand is higher than the current supply, the system is overloaded.
        // The available supply is distributed based on the current distribution function.

        if (this.totalIncomingDemand > this.currentIncomingSupply) {
            this.overloaded = true

            val supplies = distributeSupply(this.incomingDemands, this.currentIncomingSupply)

            for (idx in consumerEdges.indices) {
                this.pushOutgoingSupply(consumerEdges[idx], unitType.ofBase(supplies[idx]))
            }
        } else {
            // If the distributor was overloaded before, but is not anymore:
            //      provide all consumers with their demand

            if (this.overloaded) {
                for (idx in consumerEdges.indices) {
                    if (outgoingSupplies[idx] !=incomingDemands[idx]) {
                        this.pushOutgoingSupply(
                            consumerEdges[idx],
                            incomingDemands[idx]
                        )
                    }
                }
                this.overloaded = false
            } else {
                for (idx in this.updatedDemands) {
                    this.pushOutgoingSupply(
                        consumerEdges[idx],
                        incomingDemands[idx]
                    )
                }
            }
        }

        updatedDemands.clear()
    }

    /**
     * Add a new consumer.
     * Set its demand and supply to 0.0
     */
    public fun addConsumerEdge(consumerEdge: FlowEdge<T>) {
        consumerEdge.consumerIndex = consumerEdges.size

        consumerEdges.add(consumerEdge)
        incomingDemands.add(unitType.zero)
        outgoingSupplies.add(unitType.zero)
    }

    public fun addSupplierEdge(supplierEdge: FlowEdge<T>) {
        this.supplierEdge = supplierEdge
        this.capacity = supplierEdge.capacity
        this.currentIncomingSupply = unitType.zero
    }

    public fun rmConsumerEdge(consumerEdge: FlowEdge<T>) {
        val idx = consumerEdge.consumerIndex

        if (idx == -1) {
            return
        }

        this.totalIncomingDemand -= consumerEdge.demand

        // Remove idx from consumers that updated their demands
        updatedDemands.remove(idx)

        consumerEdges.removeAt(idx)
        incomingDemands.removeAt(idx)
        outgoingSupplies.removeAt(idx)

        // update the consumer index for all consumerEdges higher than this.
        for (i in idx until consumerEdges.size) {
            val other = consumerEdges[i]

            other.consumerIndex -= 1
        }

        val newUpdatedDemands = mutableSetOf<Int>()

        for (idx_other in this.updatedDemands) {
            if (idx_other > idx) {
                newUpdatedDemands.add(idx_other - 1)
            } else {
                newUpdatedDemands.add(idx_other)
            }
        }

        this.updatedDemands = newUpdatedDemands

        this.outgoingDemandUpdateNeeded = true
        this.invalidate()
    }

    public fun rmSupplierEdge() {
        updatedDemands.clear()
        this.closeNode()
    }

    public fun handleDemand(consumerEdge: FlowEdge<T>, newDemand: T) {
        val idx = consumerEdge.consumerIndex

        if (idx == -1) {
            println("Error (FlowDistributor): Demand pushed by an unknown consumer")
            return
        }

        // Update the total demand (This is cheaper than summing over all demands)
        val prevDemand = incomingDemands[idx]

        incomingDemands[idx] = newDemand
        this.totalIncomingDemand += (newDemand - prevDemand)

        updatedDemands.add(idx)

        this.outgoingDemandUpdateNeeded = true
        this.invalidate()
    }

    public fun handleSupply(supplierEdge: FlowEdge<T>, newSupply: T) {
        this.currentIncomingSupply = newSupply

        this.invalidate()
    }

    public override fun <U : Unit<U>> addDfltConsumerEdge(flowEdge: FlowEdge<U>) {
        if (flowEdge.unitType != this.unitType) {
            throw RuntimeException("Incompatible $flowEdge connected to $this, different `Unit`")
        }

        @Suppress("UNCHECKED_CAST")
        addConsumerEdge(flowEdge as FlowEdge<T>)
    }

    public override fun <U : Unit<U>> addDfltSupplierEdge(flowEdge: FlowEdge<U>) {
        if (flowEdge.unitType != this.unitType) {
            throw RuntimeException("Incompatible $flowEdge connected to $this, different `Unit`")
        }

        @Suppress("UNCHECKED_CAST")
        addSupplierEdge(flowEdge as FlowEdge<T>)
    }

    public fun pushOutgoingSupply(consumerEdge: FlowEdge<T>, newSupply: T) {
        val idx = consumerEdge.consumerIndex

        if (idx == -1) {
            println("Error (FlowDistributor): pushing supply to an unknown consumer")
        }

        if (outgoingSupplies[idx] == newSupply) {
            return
        }

        outgoingSupplies[idx] = newSupply
        consumerEdge.pushSupply(newSupply)
    }

    /**
     * Distributed the available supply over the different demands.
     * The supply is distributed using MaxMin Fairness.
     *
     * @return double
     */
    @OptIn(Unit.UnsafeUnitOperation::class)
    private fun distributeSupply(demands: ArrayList<T>, currentSupply: T): DoubleArray {
        val inputSize = demands.size

        val supplies = DoubleArray(inputSize)
        val tempDemands = arrayOfNulls<Demand>(inputSize)

        for (i in 0 until inputSize) {
            tempDemands[i] = Demand(i, demands[i])
        }

        Arrays.sort(tempDemands) { o1: Demand?, o2: Demand?->
            val i1 = o1!!.value
            val i2 = o2!!.value
            i1.compareTo(i2)
        }

        var availableCapacity = currentSupply // totalSupply

        for (i in 0 until inputSize) {
            val d = tempDemands[i]!!.value

            if (d.isZero()) {
                continue
            }

            val availableShare = availableCapacity / (inputSize - i)
            val r = minOf(d, availableShare)

            val idx = tempDemands[i]!!.idx
            supplies[idx] = r.toBase() // Update the rates
            availableCapacity -= r
        }

        return supplies
    }

    private inner class Demand(val idx: Int, val value: T)
}
