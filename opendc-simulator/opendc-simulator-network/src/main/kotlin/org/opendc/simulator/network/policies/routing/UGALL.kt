package org.opendc.simulator.network.policies.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.max

/**
 * TODO
 *
 * Approximation, load based routing is recomputed only when new flow arrives at node,
 * not for each single packet.
 *
 * The tx [Port] is selected considering both the path length and the congestion at the port.
 */
@Serializable
@SerialName("ugall")
internal class UGALL : RoutPolicy() {
    override val internetRoutPolicy: RoutPolicy = this

    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFEntry: NodeFlowEntry) {
        val f = nodeFEntry.netFlow
        nodeFEntry.txlinks.clear()

        // Ports the flow will be forwarded to.
        val paths = this@Node.routTbl.getPossiblePathsTo(f.destId)

        assert(paths.map { it.nextHop }.toSet().size == paths.size)

        var scoreSum = .0
        paths.map { p ->
            val port = p.associatedLink()
            val availableBw = port.availableBw

            port to max(10 - p.distance, 1) * (availableBw max (port.maxBw / 100)).tobps().also {
                scoreSum += it
            }
        }.forEach { (port, score) ->
            val prop =
                if (scoreSum == .0) Percentage.zero
                else Percentage.ofRatio(score / scoreSum)
            if (prop.isZero().not()) nodeFEntry.txlinks[port] = prop
        }

//        // Choose path considering both path length and congestion at the port.
//        val chosenPath = paths.minBy { path ->
//            // Port to send to if this path is used.
//            val port = path.associatedPort()
//
//            // Score for the port, less is better.
//            path.distance * port.txLink!!.util.toRatio()
//            path.distance * (nodeFlowEntry.rx - port.txLink!!.availableBw).tobps()
//        }
//
//        // Set the chosen port as the only one that will handle the outgoing flow.
//        nodeFlowEntry.txlinks[chosenPath.associatedPort()] = Percentage.ofPercentage(100)
    }
}
