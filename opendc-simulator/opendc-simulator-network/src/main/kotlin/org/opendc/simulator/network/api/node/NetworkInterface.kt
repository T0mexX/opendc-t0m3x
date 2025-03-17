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

package org.opendc.simulator.network.api.node

import kotlinx.coroutines.runBlocking
import org.opendc.common.logger.errAndNull
import org.opendc.common.logger.logger
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.FlowId
import org.opendc.simulator.network.api.NetFlow
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshotOf
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.Internet
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.utils.`observable-old`.ChangeHndlr
import org.opendc.simulator.network.utils.`observable-old`.SusChangeHndlr

/**
 * Type alias for improved understandability.
 */
public typealias NodeId = Long

/**
 * Interface through which control networking of a single node.
 *
 * @param[node]             the node this interface is part of.
 * @param[netController]    the controller that controls the network [node] is part of.
 * @param[owner]            the name of the owner if any.
 */
public class NetworkInterface internal constructor(
    private val node: EndPointNode,
    internal val netController: NetworkController,
    public var owner: String = "unknown",
) : AutoCloseable {
    /**
     * Physical id associated with this node.
     */
    public val nodeId: NodeId = node.id

    /**
     * Currently available upload bandwidth.
     */
    public val availableBwOut: DataRate
        get() = node.flowHandler.availableBW

    /**
     * The interfaces created by [getSubInterface]. These interfaces are closed whenever *this* is closed.
     */
    private val subInterfaces = mutableListOf<NetworkInterface>()

    /**
     * Stores [NetFlow]s started by this interface by their [FlowId]
     */

    internal val flowsById = mutableMapOf<FlowId, NetFlow>()

    /**
     * Stores [NetFlow]s started by this interface by their name (if any).
     */
    private val flowsByName = mutableMapOf<String, NetFlow>()

    /**
     * Stores [NetFlow]s started through this interface with [fromInternet] method by their [FlowId].
     */
    private val genFromInternet = mutableMapOf<FlowId, NetFlow>()

    /**
     * @return a snapshot of the [Node] this interface belongs to.
     * @see[NodeSnapshot]
     */
    public fun nodeSnapshot(): NodeSnapshot = netController.snapshotOf(node.id)!!

    /**
     * @return a new [NetworkInterface] that depends on this interface.
     * When this interface is closed, its children are also closed.
     */
    @JvmOverloads
    public fun getSubInterface(owner: String = "unknown"): NetworkInterface {
        val newIface =
            NetworkInterface(
                node = this.node,
                netController = this.netController,
                owner = owner,
            )
        subInterfaces.add(newIface)

        return newIface
    }

    /**
     * Starts a network flow ([NetFlow]) from this node to the node with id [destinationId].
     *
     * If [destinationId] is null than the flow is directed to the [Internet].
     *
     * @param[destinationId]                the id of the destination node.
     * @param[demand]              the initial data rate of the new flow.
     * @param[throughputSusChangeHndlr]      a function that is invoked whenever the
     * throughput of the flow changes. The function second parameter is the old value,
     * while the third one is the new value.
     *
     * @return a handle on the flow. One is able
     * to adjust the desired data rate modifying [NetFlow.demand].
     */
    @JvmSynthetic
    public suspend fun startFlow(
        destinationId: NodeId = INTERNET_ID,
        throughputSusChangeHndlr: SusChangeHndlr<NetFlow, DataRate>? = null,
        throughputChangeHndlr: ChangeHndlr<NetFlow, DataRate>? = null,
        demand: DataRate = DataRate.zero,
    ): NetFlow? {
        val newFlow: NetFlow? =
            netController.startFlow(
                transmitterId = this.nodeId,
                destinationId = destinationId,
                demand = demand,
                throughputSusChangeHndlr = throughputSusChangeHndlr,
                throughputChangeHndlr = throughputChangeHndlr,
            )?.also {
                flowsById[it.id] = it
            }

        return newFlow
    }

    /**
     * @see startFlow
     */
    @JvmOverloads
    public fun startFlowJava(
        destinationId: NodeId = INTERNET_ID,
        throughputChangeHndlr: ChangeHndlr<NetFlow, DataRate>? = null,
    ): NetFlow? = runBlocking { startFlow(destinationId, null, throughputChangeHndlr) }

    /**
     * Stops the flow with id [id] if it exists, and it belongs to this node.
     */
    @JvmSynthetic
    public suspend fun stopFlow(id: FlowId): NetFlow? =
        flowsById.remove(id)?.let {
            netController.stopFlow(id)
        } ?: genFromInternet.remove(id)?.let {
            netController.internetNetworkInterface.stopFlow(id)
        } ?: log.errAndNull(
            "network interface with owner '$owner' tried to stop flow which does not own",
        )

    /**
     * @see stopFlow
     */
    public fun stopFlowJava(id: FlowId): NetFlow? = runBlocking { stopFlow(id) }

    /**
     * @return a handle on the flow with id [id] if it exists, and it belongs to this node.
     */
    public fun getFlow(id: FlowId): NetFlow? = flowsById[id]

    public fun getFlow(name: String): NetFlow? = flowsByName[name]

    /**
     * Starts a flow from the [Internet] to this node.
     * Alternatively one can use [NetworkController.internetNetworkInterface].
     * A flow started through this method cannot be retrieved from this interface afterwords.
     *
     * See [startFlow] for parameter docs.
     */
    @JvmSynthetic
    public suspend fun fromInternet(
        demand: DataRate = DataRate.zero,
        throughputSusChangeHndlr: SusChangeHndlr<NetFlow, DataRate>? = null,
        throughputChangeHndlr: ChangeHndlr<NetFlow, DataRate>? = null,
    ): NetFlow {
        val newFlow: NetFlow =
            netController.internetNetworkInterface.startFlow(
                destinationId = this.nodeId,
                demand = demand,
                throughputSusChangeHndlr = throughputSusChangeHndlr,
                throughputChangeHndlr = throughputChangeHndlr,
            )!!

        genFromInternet[newFlow.id] = newFlow

        return newFlow
    }

    /**
     * @see fromInternet
     */
    @JvmOverloads
    public fun fromInternetJava(throughputChangeHndlr: ChangeHndlr<NetFlow, DataRate>? = null): NetFlow =
        runBlocking { fromInternet(throughputChangeHndlr = throughputChangeHndlr) }

    override fun close(): Unit =
        runBlocking {
            subInterfaces.forEach { it.close() }
            flowsById.keys.forEach { netController.stopFlow(it) }
            flowsById.clear()
            genFromInternet.keys.forEach { netController.stopFlow(it) }
            genFromInternet.clear()
        }

    public companion object {
        internal val log by logger()
    }
}
