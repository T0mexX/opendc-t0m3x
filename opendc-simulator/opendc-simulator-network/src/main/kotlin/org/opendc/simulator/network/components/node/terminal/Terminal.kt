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

package org.opendc.simulator.network.components.node.terminal

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.CoroutineName
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.node.NodeSpecs
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.internalstructs.flowtable.FlowTable
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.NetCoId

/**
 * TODO
 */
internal class Terminal private constructor(
    ip: IPv4Address,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var enModel: EnModel<Terminal>,
    override val flowTbl: FlowTable,
    override val stabilizer: NetSimStabilizer,
) : SenderNode<Terminal>(ip, nPorts) {
    override fun toSpecs(): NodeSpecs<Terminal> =
        TerminalSpecs(
            ip = ip,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    override fun toString(): String = "Terminal(ip=$ip)"

    companion object {
        context(NetSimScope)
        @OptIn(ProtectedUse::class)
        suspend operator fun invoke(
            ip: IPv4Address? = null,
            subnet: IPv4Address? = addrMngr.globalPrefix,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            enModel: EnModel<Terminal>? = null,
        ): Terminal {
            val nodeConfig = devConfig.nodeConfig
            val hostConfig = nodeConfig.terminalConfig

            // Assert both subnet has been claimed.
            subnet?.let { assert(addrMngr.isAddrClaimed(it)) }

            // Assert `ip` is not a subnet.
            ip?.let { assert(it.isPrefixBlock.not()) }

            return Terminal(
                ip =
                    ip?.let {
                        // Claim ip.
                        addrMngr.claimIp(ip)
                        // Assert `subnet` is the most specific subnet `ip` is in.
                        assert(addrMngr.getSubnetOf(ip) == subnet)
                        it

                        // Retrieve new unused ip address in subnet (subnet can also be 0.0.0.0/0)
                    } ?: addrMngr.getNewIp(subNet = subnet),
                portSpeed =
                    portSpeed
                        ?: hostConfig.defaultPortSpeed
                        ?: nodeConfig.defaultPortSpeed!!,
                nPorts =
                    nPorts
                        ?: hostConfig.defaultNPorts
                        ?: nodeConfig.defaultNPorts!!,
                enModel =
                    enModel
                        ?: hostConfig.defaultEnModel
                        ?: nodeConfig.defaultEnModel,
                flowTbl = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer(Terminal::class),
            ).also { t ->
                t.invalidate()

                //
                // Start the coroutine that runs the terminal.
                val coId = NetCoId.new(NetCo.NODE)
                val coName = CoroutineName("NetTerminal(id:${coId.value})")
                t.netRun(coId + coName)
            }
        }
    }
}
