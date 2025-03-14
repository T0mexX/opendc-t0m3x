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

package org.opendc.simulator.network.components.node.inet

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.CoroutineName
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.units.DataRate
import org.opendc.common.units.Power
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.NodeSpecs
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.internalstructs.flowtable.FlowTable
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.NetCoId

internal class Internet(
    override val flowTbl: FlowTable,
    override val stabilizer: NetSimStabilizer,
    inetIp: IPv4Address,
) : SenderNode<Internet>(inetIp, 0) {
    override val portSpeed: DataRate = DataRate.max
    override var nPorts: Int = 0
    override val links = mutableListOf<Link?>()

    override fun getFreeLinkIdx(): Int =
        links.indexOfFirst {
            it == null
        }.takeIf { it != -1 }
            ?: let {
                links.add(null)
                nPorts++
                links.size - 1
            }

    override fun toSpecs(): NodeSpecs<Internet> = throw RuntimeException("Internet does not have specs")

    /**
     * TODO
     */
    override val enModel = EnModel<Internet> { Power.zero }

    companion object {
        context(NetSimScope)
        @OptIn(ProtectedUse::class)
        suspend operator fun invoke(): Internet {
            // Claim the ip reserved for the representation
            // of the internet absract node in the network.
            addrMngr.claimIp(INTERNET_ID.toIp())

            return Internet(
                flowTbl = devConfig.nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer(Internet::class),
                inetIp = INTERNET_ID.toIp(),
            ).also { inet ->
                inet.invalidate()

                //
                // Start the coroutine that runs the inet node.
                val coId = NetCoId.new(NetCo.NODE)
                val coName = CoroutineName("Inet(id:${coId.value})")
                inet.netRun(coId + coName)
            }
        }
    }
}
