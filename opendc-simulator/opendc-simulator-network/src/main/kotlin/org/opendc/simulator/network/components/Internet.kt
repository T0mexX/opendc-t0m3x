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

import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.port.PortImpl
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP

/**
 * This 'abstract' node represents the internet.
 * It has infinite number of ports and port speed.
 */
internal class Internet(
    override val portSelectionPolicy: PortSelectionPolicy = StaticECMP,
) : EndPointNode {
    /**
     * Has no effect since the node has infinite port speed.
     */
    override val fairnessPolicy: FairnessPolicy = FirstComeFirstServed
    override val updtChl = UpdateChl()

    override val id: NodeId = INTERNET_ID

    private fun addPort() {
        ports.add(PortImpl(maxSpeed = portSpeed, owner = this))
    }

    override val portSpeed: DataRate = DataRate.ofKbps(Double.MAX_VALUE)
    override val ports = mutableListOf<Port>()
        get() {
            if (portToNode.size == field.size) {
                field.add(PortImpl(maxSpeed = portSpeed, owner = this))
            }
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

    override fun toSpecs(): Specs<Node> {
        throw RuntimeException("Internet does not have specs")
    }

    override fun toString(): String = "[INTERNET_NODE]"
}
