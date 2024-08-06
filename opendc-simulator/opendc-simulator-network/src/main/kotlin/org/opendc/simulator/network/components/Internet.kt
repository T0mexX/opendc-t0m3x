package org.opendc.simulator.network.components

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.port.PortImpl
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMinNoForcedReduction
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.units.DataRate

internal class Internet(
    override val portSelectionPolicy: PortSelectionPolicy = StaticECMP
): EndPointNode {

    override val fairnessPolicy: FairnessPolicy = MaxMinNoForcedReduction
    override val updtChl = UpdateChl()

    override val id: NodeId = INTERNET_ID



    private fun addPort() { ports.add(PortImpl(maxSpeed = portSpeed, owner = this)) }

    override val portSpeed: DataRate = DataRate.ofKbps(Double.MAX_VALUE)
    override val ports = mutableListOf<Port>()
        get() {
            if (portToNode.size == field.size)
                field.add(PortImpl(maxSpeed = portSpeed, owner = this))
            return field
        }
    override val flowHandler: FlowHandler
    override val routingTable = RoutingTable(this.id)

    override val portToNode = mutableMapOf<NodeId, Port>()

    init {
        flowHandler = FlowHandler(ports) // for some reason if joined with assignment won't work
    }


    fun connectedTo(coreSwitches: Collection<CoreSwitch>): Internet {
        coreSwitches.forEach {
            runBlocking { it.connect(this@Internet) }
        }

        return this
    }

    suspend fun connect(other: Node) {
        addPort()

        // calls extension function
        connect(other, duplex = true)
    }

    override fun toString(): String =
        "[INTERNET_NODE]"
}
