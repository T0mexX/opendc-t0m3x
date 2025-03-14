/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.node.NodeId
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.port.PortImpl
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.energy.emodels.SwitchDfltEnModel
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.policies.forwarding.OSPF
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.IdDispenser

/**
 * This [Node] represents a switch in the network. This switch routs incoming flows
 * according to its [fairnessPolicy] and [portSelectionPolicy].
 * Depending on the policies, the switch can provide SDN routing.
 */
internal open class Switch(
    final override val id: NodeId,
    override val portSpeed: DataRate,
    override val numOfPorts: Int,
    override val fairnessPolicy: FairnessPolicy = MaxMinPerPort,
    override val portSelectionPolicy: PortSelectionPolicy = StaticECMP,
) : Node, EnergyConsumer<Switch> {
    override val updtChl = UpdateChl()
    override val enMonitor: EnMonitor<Switch> by lazy { EnMonitor(this) }
    override val routingTable: RoutingTable = RoutingTable(this.id)
    override val portToNode: MutableMap<NodeId, Port> = HashMap()
    final override val ports: List<Port> =
        buildList {
            repeat(numOfPorts) {
                add(PortImpl(maxSpeed = portSpeed, owner = this@Switch))
            }
        }
    override val flowHandler = FlowHandler(ports)

    override suspend fun consumeUpdt() {
        super.consumeUpdt()
        enMonitor.update()
    }

    override fun getDfltEnModel(): EnModel<Switch> = SwitchDfltEnModel

    override fun toSpecs(): Specs<Switch> =
        SwitchSpecs(
            id = id,
            numOfPorts = numOfPorts,
            portSpeed = portSpeed,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )

    override fun toString(): String =
        "Switch(id=$id, portSpeed=$portSpeed, " +
            "numOfPorts=$numOfPorts, " +
            "fairnessPolicy=$fairnessPolicy, " +
            "portSelectionPolicy=$portSelectionPolicy)"

    /**
     * Serializable representation of the specifics from which a switch can be built.
     */
    @Serializable
    @SerialName("switch-specs")
    internal data class SwitchSpecs(
        val numOfPorts: Int,
        val portSpeed: DataRate,
        val id: NodeId? = null,
        val fairnessPolicy: FairnessPolicy = FirstComeFirstServed,
        val portSelectionPolicy: PortSelectionPolicy = OSPF,
    ) : Specs<Switch> {
        override fun build(): Switch =
            Switch(
                id = id ?: IdDispenser.nextNodeId,
                portSpeed = portSpeed,
                numOfPorts,
                fairnessPolicy = fairnessPolicy,
                portSelectionPolicy = portSelectionPolicy,
            )

        fun buildCoreSwitchFromSpecs(): CoreSwitch =
            CoreSwitch(
                id = id ?: IdDispenser.nextNodeId,
                portSpeed = portSpeed,
                // 1 more port connect to Internet "node"
                numOfPorts = numOfPorts + 1,
                fairnessPolicy = fairnessPolicy,
                portSelectionPolicy = portSelectionPolicy,
            )
    }
}
