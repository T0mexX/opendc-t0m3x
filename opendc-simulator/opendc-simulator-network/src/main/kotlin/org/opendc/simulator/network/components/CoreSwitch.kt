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
import org.opendc.simulator.network.components.stability.NetworkStabilityBarrier
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.policies.forwarding.OSPF
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.IdDispenser

/**
 * // TODO: remove end-point-node capabilities
 * This switch is automatically connected to the internet (for now).
 * @see Switch
 */
internal class CoreSwitch(
    id: NodeId,
    portSpeed: DataRate,
    numOfPorts: Int,
    fairnessPolicy: FairnessPolicy = MaxMinPerPort,
    portSelectionPolicy: PortSelectionPolicy = StaticECMP,
) : Switch(id, portSpeed, numOfPorts, fairnessPolicy, portSelectionPolicy), EndPointNode {
    override suspend fun consumeUpdt() {
        super<EndPointNode>.consumeUpdt()
        enMonitor.update()
    }

    override suspend fun run(invalidator: NetworkStabilityBarrier.Invalidator?) {
        return super<EndPointNode>.run(invalidator)
    }

    override fun toString(): String =
        "CoreSwitch(id=$id, " +
            "portSpeed=$portSpeed, " +
            "numOfPorts=$numOfPorts, " +
            "fairnessPolicy=$fairnessPolicy, " +
            "portSelectionPolicy=$portSelectionPolicy)"

    override fun toSpecs(): Specs<CoreSwitch> =
        CoreSwitchSpecs(
            id = id,
            numOfPorts = numOfPorts,
            portSpeed = portSpeed,
            fairnessPolicy = fairnessPolicy,
            portSelectionPolicy = portSelectionPolicy,
        )

    /**
     * Serializable representation of the specifics from which a core switch can be built.
     * Core switches in [CustomNetwork]s are automatically connected to the internet.
     */
    @Serializable
    @SerialName("core-switch-specs")
    internal data class CoreSwitchSpecs(
        val numOfPorts: Int,
        val portSpeed: DataRate,
        val id: NodeId? = null,
        val fairnessPolicy: FairnessPolicy = FirstComeFirstServed,
        val portSelectionPolicy: PortSelectionPolicy = OSPF,
    ) : Specs<CoreSwitch> {
        override fun build(): CoreSwitch =
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
