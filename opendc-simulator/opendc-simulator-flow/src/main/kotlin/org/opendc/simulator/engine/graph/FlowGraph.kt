package org.opendc.simulator.engine.graph

import org.opendc.common.units.Unit
import org.opendc.common.units.UnitType
import org.opendc.simulator.engine.engine.FlowEngine


public class FlowGraph(
    /**
     * Return the [FlowEngine] driving the simulation of the graph.
     */
    public val engine: FlowEngine
) {
    private val nodes = ArrayList<FlowNode>()
    private val edges = ArrayList<FlowEdge<*>>()
    private val nodeToEdge = HashMap<FlowNode, ArrayList<FlowEdge<*>>>()

    /**
     * Create a new [FlowNode] representing a node in the flow network.
     */
    public fun addNode(node: FlowNode) {
        if (nodes.contains(node)) {
            println("Node already exists")
        }
        nodes.add(node)
        nodeToEdge[node] = ArrayList()
        val now = engine.clock.millis()
        node.invalidate(now)
    }

    /**
     * Internal method to remove the specified [FlowNode] from the graph.
     */
    public fun removeNode(node: FlowNode) {
        // Remove all edges connected to node

        val connectedEdges = nodeToEdge.getOrDefault(node, emptyList())
        while (connectedEdges.isNotEmpty()) {
            removeEdge(connectedEdges[0])
        }

        nodeToEdge.remove(node)

        // remove the node
        nodes.remove(node)
    }

    /**
     * Add an edge between the specified consumer and supplier in this graph.
     */
    public fun <T: Unit<T>> addEdge(unitType: UnitType<T>, flowConsumer: FlowNode, flowSupplier: FlowNode): FlowEdge<T> {
        // Check of the consumer and supplier are present in this graph
        require((nodes.contains(flowConsumer))) { "The consumer is not a node in this graph" }
        require((nodes.contains(flowSupplier))) { "The supplier is not a node in this graph" }

        val flowEdge: FlowEdge<T> = FlowEdge(unitType)
        flowSupplier.addDfltSupplierEdge(flowEdge)
        flowConsumer.addDfltConsumerEdge(flowEdge)

        edges.add(flowEdge)

        nodeToEdge[flowConsumer]!!.add(flowEdge)
        nodeToEdge[flowSupplier]!!.add(flowEdge)

        return flowEdge
    }
    public fun removeEdge(flowEdge: FlowEdge<*>) {
        val consumer = flowEdge.consumer
        val supplier = flowEdge.supplier
        nodeToEdge[consumer]!!.remove(flowEdge)
        nodeToEdge[supplier]!!.remove(flowEdge)

        edges.remove(flowEdge)
        flowEdge.close()
    }
}
