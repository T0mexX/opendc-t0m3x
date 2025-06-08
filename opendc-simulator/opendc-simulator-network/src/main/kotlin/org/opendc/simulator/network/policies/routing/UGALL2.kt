package org.opendc.simulator.network.policies.routing

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.common.units.Percentage.Companion.percentageOf
import org.opendc.common.units.Unit.Companion.sumOfUnit
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 *
 * Approximation, load based routing is recomputed only when new flow arrives at node,
 * not for each single packet.
 *
 * The tx [Port] is selected considering both the path length and the congestion at the port.
 */
@Serializable
@SerialName("ugall2")
internal class UGALL2 : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = this

    context(NetSimScope)
    override suspend fun checkRequirements() {
        require(devConfig.netConfig.includeRoutInfo2Switches)
    }


    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFEntry: NodeFlowEntry) {
        val f = nodeFEntry.netFlow
        assert(f.destId != this@Node.id)

        if (f.senderNode === this@Node) {
            this@Node as HostNode
            ifSenderHost(nodeFEntry)
        } else ifNotSenderHost(nodeFEntry)
    }

    context(NetSimScope)
    override suspend fun onFlowStart(f: INetFlow) {
        // If this is first time method is called for this flow, then create subflows for intermediate nodes.
        with(f.senderNode) {
            initSubFlows(f)
        }
    }

    context(NetSimScope, Node<*>)
    private suspend fun ifNotSenderHost(nodeFEntry: NodeFlowEntry) {
        val f = nodeFEntry.netFlow

        // If the flow still needs to be routed to the intermediate node, then do so.
        if (nodeFEntry.toIntermediate)
            nodeFEntry.txPorts[MIN.selectPort(f.intermediate!!)] = Percentage.ofPercentage(100)
        // Else rout it to destination.
        else
            nodeFEntry.txPorts[MIN.selectPort(f.destId)] = Percentage.ofPercentage(100)
    }

    context(NetSimScope, SenderNode<*>)
    private suspend fun ifSenderHost(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        val fDemand = f.demand

        // Ignore subflows.
        if (f.parentFlow != null) return

        //
        // Compute the score for each possible valiant path.
        var scoreSum = .0
        f.subFlows.forEach { subF ->
            val meta = subF.routMeta as UGALLRoutMeta
            val portUtil = meta.port!!.txLink!!.util

            meta.currScore = (1 / (meta.length!! * portUtil.toRatio())).takeUnless { it.isInfinite() } ?: (.001 / meta.length)
            scoreSum += meta.currScore
        }


        // Distribute data rate proportionally to the scores.
        f.subFlows.forEach { subF ->
            val meta = subF.routMeta as UGALLRoutMeta

            subF.subFPerc = meta.currScore percentageOf scoreSum
            assert(subF.subFPerc!! approxSmallerOrEq Percentage.ofPercentage(100)) { subF.subFPerc!! }
            subF.setDemand(fDemand * subF.subFPerc!!)
        }

        println("${f.senderId.toIp()} -> ${f.destId.toIp()} ${f.subFlows.map { Triple(it.subFPerc, it.intermediate?.toIp() ?: NodeId(-10).toIp(), (it.routMeta as UGALLRoutMeta).length) }}")
        // Assert the distribution sums up to 100% of the parent flow demand.
        assert(
            f.subFlows.sumOfUnit {
                it.subFPerc!!
            } approx Percentage.ofPercentage(100)
        ) { f.subFlows.sumOfUnit {
            it.subFPerc!!
        } }
    }

    context(NetSimScope, SenderNode<*>)
    private suspend fun initSubFlows(f: INetFlow) {
        val ints = possibleIntermediates(f)
        val senderN = f.senderNode

        ints.forEach { (int, pair) ->
            val pathLength = pair.second
            val port = pair.first
            val subF = f.subFlow(intermediate = int.id)
            val nodeSubFEntry = senderN.flowTable[subF]
            nodeSubFEntry.txPorts[port] = Percentage.ofPercentage(100)
            subF.routMeta = UGALLRoutMeta(
                length = pathLength,
                port = port,
                hostFlowEntry = nodeSubFEntry,
            )
        }

        val minPath = f.senderNode.routTbl.getPossiblePathsTo(f.destId).onlyMinimal().first()
        val minSubF = f.subFlow()
        val port = minPath.associatedPort()
        val entry = f.senderNode.flowTable[minSubF]
        entry.txPorts[port] = Percentage.ofPercentage(100)
        f.routMeta = UGALLRoutMeta(minSubF = minSubF)

        minSubF.routMeta = UGALLRoutMeta(
            length = minPath.distance,
            port = port,
            hostFlowEntry = entry,
        )
    }

    /**
     * TODO
     * possible intermidiate nodes mapped to the port on the hostnode that should be used.
     */
    context(NetSimScope, SenderNode<*>)
    private suspend fun possibleIntermediates(f: INetFlow): Map<Node<*>, Pair<Port, Int>> {
        // Smallest subnet containing both sender and receiver nodes.
        val subnet = smallestCommonSubnet(f.senderId.toIp(), f.destId.toIp())

        // Nodes in `subnet`, the ones considered as possible intermediates.
        return net.nodeLs.mapNotNull { inter ->
            if (inter.ip !in subnet) return@mapNotNull null
            if (inter === f.senderNode || inter.id == f.destId) return@mapNotNull null

            // The `MIN` path from sender node to possible intermediate.
            val pathToInter = f.senderNode.routTbl.getPossiblePathsTo(inter.id).onlyMinimal().first()
            // The `MIN` path from possible intermediate to destination.
            val pathToDest = inter.routTbl.getPossiblePathsTo(f.destId).onlyMinimal().first()

            // Avoid possible intermediates that have same nodes in 'toIntermediate' and 'toDest' path.
            if (pathToInter.addrHops.dropLast(1).any { it in pathToDest.addrHops }) return@mapNotNull null

            val pathLength = pathToDest.distance + pathToInter.distance - 1
            inter to Pair(pathToInter.associatedPort(), pathLength)
        }.toMap().also { println("possible valiants number: ${it.size}") }
    }

    context(NetSimScope)
    private suspend fun smallestCommonSubnet(first: IPv4Address, second: IPv4Address): IPv4Address {
        assert(first.isPrefixed.not())
        assert(second.isPrefixed.not())

        val addrMngr = this@NetSimScope.addrMngr

        var a: IPv4Address = first
        var b: IPv4Address = second

        while (a != b) {
            if ((a.prefixLength ?: IPv4Address.BIT_COUNT) > (b.prefixLength ?: IPv4Address.BIT_COUNT))
                a = addrMngr.getSubnetOf(a)!!
            else b = addrMngr.getSubnetOf(b)!!
        }

        return a
    }

    /**
     * @property minSubF **if parent flow**: the MIN subflow (not valiant).
     * @property length length of the valiant path for the subflow.
     * @property port the port associated with the valiant path on the sender [HostNode].
     * @property currScore the last computed score for the distribution of data rate among valiant paths.
     * @property hostFlowEntry the entry in the [HostNode]~[FlowTable] corresponding to this subflow.
     */
    data class UGALLRoutMeta(
        val minSubF: INetFlow? = null,
        val length: Int? = null,
        val port: Port? = null,
        var currScore: Double = .0,
        val hostFlowEntry: NodeFlowEntry? = null,
    ) : RoutMeta<UGALL2>
}
