package org.opendc.simulator.network.components.node

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.FlowView
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.WithSpecs
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.sync.invalidatable.Invalidatable


/**
 * Interface representing a node in a [Network2].
 */
internal interface Node : FlowView, WithSpecs<Node>, Invalidatable {
    /**
     * ID of the node. Uniquely identifies the node in the [Network22].
     */
    val id: NodeId2

    /**
     * Port speed in Kbps full duplex.
     */
    val portSpeed: DataRate

    /**
     * Number of ports of ***this*** [Node].
     */
    val numOfPorts: Int get() {
        return ports.size
    }



    /**
     * Policy that determines to which [Port]s the flows are forwarded to.
     */
    val portSelectionPolicy: PortSelectionPolicy

    /**
     * Policy that determines how the flows data are handled in case of maximum bw reached.
     */
    val fairnessPolicy: FairnessPolicy

    /**
     * Handles incoming and outgoing flows.
     */
    val flowHandler: FlowHandler

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routingTable: RoutingTable

    /**
     * Maps each connected [Node]'s id to the [Port] is connected to.
     */
    val portToNode: MutableMap<NodeId2, Port>

    /**
     * Property returning the number of [Node]s connected to ***this***.
     */
    private val numOfConnectedNodes: Int
        get() {
            return ports.count { it.isConnected }
        }

    /**
     * Aggregates the flow updates from all adjacent nodes.
     */
    val updtChl: UpdateChl

    /**
     * **Does not return**. Should be launched as an independent coroutine.
     * Processes incoming updates.
     * @param[invalidator] used to invalidate the network stability while updates are pending or being processed.
     */
    suspend fun run(invalidator: NetworkStabilityBarrier.Invalidator? = null) {
        invalidator?.let { updtChl.withInvalidator(invalidator) }
        updtChl.clear()

        while (true) {
            yield()
            consumeUpdt()
        }
    }

    /**
     * Consumes a round of updates.
     */
    suspend fun consumeUpdt() {
        var updt: RateUpdt = updtChl.receive()
        while (true) {
            yield()
            updtChl.tryReceiveSus().getOrNull()
                ?.also { updt = updt.merge(it) }
                ?: break
        }

        flowHandler.updtFlows(updt)

        notifyAdjNodes()
    }

    /**
     * Sends the buffered updates to adjacent nodes.
     */
    private suspend fun notifyAdjNodes() {
        portToNode.values.forEach { it.notifyReceiver() }
    }

    /**
     * Updates forwarding of all flows transiting through ***this*** node.
     */
    suspend fun updateAllFlows() {
        updtChl.send(RateUpdt(allTransitingFlowsIds().associateWith { DataRate.zero })) // TODO: change
    }

    override suspend fun totIncomingDataRateOf(fId: FlowId): DataRate = flowHandler.outgoingFlows[fId]?.demand.ifNull0()

    override fun totOutgoingDataRateOf(fId: FlowId): DataRate = flowHandler.outgoingFlows[fId]?.totRateOut.ifNull0()

    override fun allTransitingFlowsIds(): Collection<FlowId> =
        with(flowHandler) {
            outgoingFlows.keys + consumingFlows.keys
        }

    /**
     * @return formatted string representing node information. Preferably to be logged in a new line.
     */
    fun fmt(): String =
        """
        | Type = ${this::class.simpleName}
        | numPorts = $numOfPorts
        | portSpeed = $portSpeed
        | numConnectedNodes = $numOfConnectedNodes
        """.trimIndent()

    override fun toSpecs(): Specs<Node> {
        TODO("Not yet implemented")
    }

    override val stabilizer: NetSimStabilizer
        get() = TODO("Not yet implemented")
}


