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

package org.opendc.simulator.network.api

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.integration.JNetIFace
import org.opendc.simulator.network.api.integration.latched
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.flow.FlowId
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
public open class NetIFace private constructor(
    private val scope: NetSimScope,
    private val t: Terminal,
) : AutoCloseable {
    /**
     * TODO
     */
    public val jNetIFace: JNetIFace by lazy { JNetIFace(scope, this) }

    /**
     * Physical id associated with this node.
     */
    public val nodeId: NodeId = t.id

    public val nodeIp: IPv4Address = t.ip

    /**
     * Stores [NetFlow]s started by this interface by their [FlowId]
     */

    internal val flowsById = mutableMapOf<FlowId, NetFlow>()

    /**
     * Stores [NetFlow]s started through this interface with [fromInternet] method by their [FlowId].
     */
    private val genFromInternet = mutableMapOf<FlowId, NetFlow>()

    /**
     * @return a snapshot of the [Node] this interface belongs to.
     * @see[NodeSnapshot]
     */
    public suspend fun nodeSnapshot(): NodeSnapshot = scope.async { t.snapshot() }.await()

    /**
     * Starts a network flow ([NetFlow]) from this node to the node with id [destId].
     *
     * If [destId] is null than the flow is directed to the [Internet].
     *
     * @param[destId]                the id of the destination node.
     * @param[dmnd]              the initial data rate of the new flow.
     *
     * @return a handle on the flow. One is able
     * to adjust the desired data rate modifying [NetFlow.demand].
     */
    @JvmSynthetic
    public suspend fun startFlow(
        destId: NodeId = INTERNET_ID,
        dmnd: DataRate = DataRate.zero,
    ): NetFlow =
        scope.asyncInRoot {
            val newF =
                scope.devConfig.netFlowConfig.version(
                    srcId = this@NetIFace.nodeId,
                    destId = destId,
                    dmnd = dmnd,
                )

            net.startFlow(newF)
            flowsById[newF.id] = newF

            newF
        }.await()

    @JvmSynthetic
    public suspend fun startFlowFromInet(dmnd: DataRate = DataRate.zero): NetFlow =
        scope.asyncInRoot {
            val newF =
                scope.devConfig.netFlowConfig.version(
                    srcId = INTERNET_ID,
                    destId = this@NetIFace.nodeId,
                    dmnd = dmnd,
                )

            net.startFlow(newF)
            genFromInternet[newF.id] = newF

            newF
        }.await()


    public suspend fun stopFlow(f: NetFlow): Unit =
        scope.launchInRoot {
            require(
                genFromInternet.remove(f.id) != null
                    || flowsById.remove(f.id) != null
            ) { "$f cannot be stopped, was not started by this ${this@NetIFace} " }
            net.stopFlow(f as INetFlow)
        }.join()

    override fun close(): Unit {
        if (scope.isActive) {
            latched(scope) {
                flowsById.values.forEach { net.stopFlow(it as INetFlow) }
                flowsById.clear()
                genFromInternet.values.forEach { net.stopFlow(it as INetFlow) }
                genFromInternet.clear()
            }
        }
    }

    internal companion object {
        context(NetSimScope)
        operator fun invoke(t: Terminal): NetIFace = NetIFace(this@NetSimScope, t)
    }
}
