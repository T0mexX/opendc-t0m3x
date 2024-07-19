package org.opendc.simulator.network.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.internalstructs.FlowHandler
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.port.PortImpl
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.emodels.SwitchDfltEnModel
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.Kbps

/**
 * A [Node] whose job is to route incoming flows according to [portSelectionPolicy].
 * @param[id]               id of this [Node].
 * @param[portSpeed]        port speed in Kbps.
 * @param[numOfPorts]       number of ports.
 * @param[portSelectionPolicy] policy used to determine the links to which forward incoming flows.
 */
internal open class Switch(
    final override val id: NodeId,
    override val portSpeed: Kbps,
    override val numOfPorts: Int,
    override val fairnessPolicy: FairnessPolicy = FirstComeFirstServed,
    override val portSelectionPolicy: PortSelectionPolicy = StaticECMP,
): Node, EnergyConsumer<Switch> {

    override val updtChl = UpdateChl()
    override val flowHandler = FlowHandler()

    override val enMonitor: EnMonitor<Switch> by lazy { EnMonitor(this) }
    override val routingTable: RoutingTable = RoutingTable(this.id)

    override val portToNode: MutableMap<NodeId, Port> = HashMap()
    override val ports: List<Port> =
        buildList { repeat(numOfPorts) {
            add(PortImpl(maxSpeed = portSpeed, owner = this@Switch))
        } }

    override suspend fun consumeUpdt() {
        super.consumeUpdt()
        enMonitor.update()
    }

    override fun getDfltEnModel(): EnModel<Switch> = SwitchDfltEnModel

    override fun toString(): String = "[Switch: id=$id]"


    /**
     * Serializable representation of the specifics from which a switch can be built.
     */
    @Serializable
    @SerialName("switch-specs")
    internal data class SwitchSpecs (
        val numOfPorts: Int,
        val portSpeed: Kbps,
        val id: NodeId? = null
    ): Specs<Switch> {
        override fun buildFromSpecs(): Switch = Switch(id = id ?: IdDispenser.nextNodeId, portSpeed, numOfPorts)

        fun buildCoreSwitchFromSpecs(): CoreSwitch = CoreSwitch(id = id ?: IdDispenser.nextNodeId, portSpeed, numOfPorts)
    }
}




