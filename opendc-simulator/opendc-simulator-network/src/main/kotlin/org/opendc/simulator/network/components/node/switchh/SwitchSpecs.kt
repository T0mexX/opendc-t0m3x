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
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeSpecs
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("switch")
internal data class SwitchSpecs(
    @Contextual val ip: IPv4Address? = null,
    private val portSpeed: DataRate? = null,
    private val nPorts: Int? = null,
    val global: Boolean = false,
) : NodeSpecs<Switch> {
    context(NetSimScope)
    override fun nPorts(): Int =
        nPorts
            ?: devConfig.nodeConfig.switchConfig.defaultNPorts
            ?: devConfig.nodeConfig.defaultNPorts!!

    context(NetSimScope)
    override fun portSpeed(): DataRate =
        portSpeed
            ?: devConfig.nodeConfig.switchConfig.defaultPortSpeed
            ?: devConfig.nodeConfig.defaultPortSpeed!!

    context(NetSimScope)
    override suspend fun build(
        subnet: IPv4Address?,
        inet: Internet?,
        updtRoutTbl: Boolean,
    ): Switch {
        // If ip is specified in specs, then claim it.
        ip?.let { addrMngr.claimIp(it) }
        if (ip != null && subnet != null) {
            assert(ip in subnet)
        }

        return Switch(
            ip = subnet,
            portSpeed = portSpeed,
            nPorts = nPorts,
            global = global,
            subnet = subnet,
        ).also { s ->
            if (global) {
                requireNotNull(inet)
                s.msgSyncConnect(inet, updtRoutTbl = updtRoutTbl)
            }
        }
    }

    context(NetSimScope)
    suspend fun buildAsGlobal(
        subnet: IPv4Address? = null,
        inet: Internet? = null,
        updtRoutTbl: Boolean,
    ): Switch = this.copy(global = true).build(subnet, inet, updtRoutTbl)
}
