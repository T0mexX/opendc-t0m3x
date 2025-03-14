/*
 * Copyright (c) 2025 AtLarge Research
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

package org.opendc.simulator.network.components.node.switchh

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.CoroutineName
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.node.NodeImpl
import org.opendc.simulator.network.components.node.NodeSpecs
import org.opendc.simulator.network.components.node.internalstructs.flowtable.FlowTable
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.NetCoId

internal class Switch private constructor(
    ip: IPv4Address,
    val global: Boolean = false,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override val enModel: EnModel<Switch>,
    override val flowTbl: FlowTable,
    override val stabilizer: NetSimStabilizer,
) : NodeImpl<Switch>(ip, nPorts), EnConsumer<Switch> {
    override fun toSpecs(): NodeSpecs<Switch> =
        SwitchSpecs(
            ip = ip,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    override fun toString(): String = "Switch(ip=$ip)"

    companion object {
        /**
         * Suspending constructor.
         */
        context(NetSimScope)
        @OptIn(ProtectedUse::class)
        internal suspend operator fun invoke(
            ip: IPv4Address? = null,
            subnet: IPv4Address? = addrMngr.globalPrefix,
            global: Boolean = false,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            enModel: EnModel<Switch>? = null,
        ): Switch {
            val nodeConfig = devConfig.nodeConfig
            val switchConfig = nodeConfig.switchConfig

            // Assert subnet has been claimed.
            subnet?.let { assert(addrMngr.isAddrClaimed(it)) }

            // Assert `ip` is not a subnet.
            ip?.let { assert(it.isPrefixBlock.not()) }

            return Switch(
                ip =
                    ip?.let {
                        // Claim ip.
                        addrMngr.claimIp(ip)
                        // Assert `subnet` is the most specific subnet `ip` is in.
                        assert(addrMngr.getSubnetOf(ip) == subnet)
                        it

                        // Retrieve new unused ip address in subnet (subnet can also be 0.0.0.0/0)
                    } ?: addrMngr.getNewIp(subNet = subnet),
                global = global,
                portSpeed =
                    portSpeed
                        ?: switchConfig.defaultPortSpeed
                        ?: nodeConfig.defaultPortSpeed!!,
                nPorts =
                    nPorts
                        ?: switchConfig.defaultNPorts
                        ?: nodeConfig.defaultNPorts!!,
                enModel =
                    enModel
                        ?: switchConfig.defaultEnModel
                        ?: nodeConfig.defaultEnModel,
                flowTbl = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer(Switch::class),
            ).also { s ->
                s.invalidate()

                //
                // Start the coroutine that runs the switch.
                val coId = NetCoId.new(NetCo.NODE)
                val coName = CoroutineName("NetSwitch(id:${coId.value})")
                s.netRun(coId + coName)
            }
        }
    }
}
