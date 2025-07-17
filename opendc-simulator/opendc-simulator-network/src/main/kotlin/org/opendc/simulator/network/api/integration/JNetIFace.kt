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

package org.opendc.simulator.network.api.integration

import org.opendc.common.annotations.DebuggingUse
import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.simulator.network.api.NetIFace
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.SetOnce

/**
 * TODO
 */
public class JNetIFace internal constructor(
    internal val scope: NetSimScope,
    private val iFace: NetIFace,
) : AutoCloseable by iFace {
    @JvmOverloads
    public fun startFlow(
        destIdL: Long = INTERNET_ID.value.toLong(),
        dmndKbps: Double = .0,
    ): JNetFlow =
        netBlking(scope) {
            val destId = NodeId(destIdL.toUInt())
            val dmnd = DataRate.ofKbps(dmndKbps)

            val f = iFace.startFlow(destId = destId, dmnd = dmnd)
            JNetFlow(f = f, scope = scope).also {
                f as INetFlow
                f.jNetFlow = it
            }
        }

    @JvmOverloads
    public fun startFlowFromInet(dmndKbps: Double = .0): JNetFlow =
        netBlking(scope) {
            val dmnd = DataRate.ofKbps(dmndKbps)

            val f = iFace.startFlowFromInet(dmnd = dmnd)
            JNetFlow(f = f, scope = scope).also {
                f as INetFlow
                f.jNetFlow = it
            }
        }

    public fun stopFlow(f: JNetFlow): Unit =
        netBlking(scope) {
            iFace.stopFlow(f.f)
        }




    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Debugging/Testing
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @DebuggingUse
    public fun netSimTmstampLong(): Long = scope.tmSrc.tmstamp.toEpochMsLong()
}
