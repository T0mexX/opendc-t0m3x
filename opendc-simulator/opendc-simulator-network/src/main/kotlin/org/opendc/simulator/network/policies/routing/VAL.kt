package org.opendc.simulator.network.policies.routing

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.internals.flowtable.NodeFlowEntry
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * **Valiant Random Routing**.
 * To route a packet,
 * the protocol first randomly selects a router Rr different from
 * Rs (sender) and Rd(destination). The packet is then routed along two minimal paths:
 * from Rs to Rr, and from Rr to Rd.
 *
 * Source: https://htor.inf.ethz.ch/publications/img/sf_sc_2014.pdf
 */
@Serializable
@SerialName("val")
internal class VAL: RoutPolicy(), MutableMap<NetFlow, RoutPath> by mutableMapOf() {
    context(NetSimScope, Node<*>)
    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
        val f = nodeFlowEntry.netFlow
        assert(nodeFlowEntry.txPorts.isEmpty())

        // Set ports to forward flow to according to `RoutPath` for flow `f`.
        this[f]!![this@Node]!!.forEach { port ->
            nodeFlowEntry.txPorts += port
        }
    }

    context(NetSimScope)
    override suspend fun onFlowStarted(f: NetFlow) {
        this[f] = f.newPath()
    }

    context(NetSimScope)
    override suspend fun onFlowStopped(f: NetFlow) {
        assert(this.remove(f) != null)
    }

    context(NetSimScope)
    override suspend fun onNodeAdded(n: Node<*>) {
        TODO()
    }

    context(NetSimScope)
    override suspend fun onNodeRemoved(n: Node<*>) {
        TODO()
    }

    /**
     * @return Random [Node] in [this] [Network], which is not among [excludedNodes].
     */
    private fun Network.randomNodeExcept(vararg excludedNodes: Node<*>): Node<*> {
        var randomIdx = nodeLs.indices.random()
        var n: Node<*>
        do {
            // The Probability you hit multiple times the same node is low.
            n = nodeLs[randomIdx]
            randomIdx = if (randomIdx == nodeLs.size - 1) 0 else randomIdx + 1
        } while (n !in excludedNodes)

        return n
    }


    context(NetSimScope)
    private suspend fun NetFlow.newPath(): RoutPath {
        val net = this@NetSimScope.net
        val sender = net[senderId]!!
        val dest = net[destId]!!

        // Get random node Rr different from Rs (sender) and Rd (destination).
        val middleRandomN = net.randomNodeExcept(sender, dest)

        // Build routing rules for this flow.
        return RoutPath(this@NetFlow) {
            var currN = sender

            // For the first part target is `middleRandomN`,
            // for the second part is the destination node.
            var target = middleRandomN
            while (currN != dest) {
                with(currN) {
                    // Select one of the ports on `currN` that leads
                    // to one of the shortest paths towards `target`.
                    val ports = OSPF.selectPorts(target)
                    assert(ports.size == 1)

                    // Add entry for the current node.
                    put(currN, ports)

                    // Update `curr` to the node to which the single `Port` in `ports` is connected.
                    currN = ports.first().txLink!!.receiverPort.owner

                    // If `middleRandomN` then the first part of VAL is completed,
                    // and we can proceed to routing from `middleRandomN`
                    // to the actual destination node.
                    if (currN == middleRandomN) target = dest
                }
            }
        }
    }
}

//@Serializable
//@SerialName("val")
//internal data object VAL: RoutPolicy {
//    /**
//     * A [Flow] to easily retrieve a random node in the network that is not the sender.
//     */
//    context(NetSimScope, Node<*>)
//    override suspend fun selectPorts(nodeFlowEntry: NodeFlowEntry) {
//        val f = nodeFlowEntry.netFlow
//
//        // If VAL context not defined yet in simulation scope, then create.
//        val valCtx = this@NetSimScope.coroutineContext[Ctx] ?: Ctx().also {
//            this@NetSimScope.coroutineContext += it
//        }
//
//        if (nodeFlowEntry.txPorts.isNotEmpty())
//
//        val routPath = valCtx[f] ?:
//
//    }
//
//    /**
//     * @return Random [Node] in [this] [Network], which is not among [excludedNodes].
//     */
//    private fun Network.randomNodeExcept(vararg excludedNodes: Node<*>): Node<*> {
//        var randomIdx = nodeLs.indices.random()
//        var n: Node<*>
//        do {
//            // The Probability you hit multiple times the same node is low.
//            n = nodeLs[randomIdx]
//            randomIdx = if (randomIdx == nodeLs.size - 1) 0 else randomIdx + 1
//        } while (n !in excludedNodes)
//
//        return n
//    }
//
//    context(NetSimScope)
//    private suspend fun NetFlow.newPath(): RoutPath {
//        val net = this@NetSimScope.net
//        val sender = net[senderId]!!
//        val dest = net[destId]!!
//
//        // Get random node Rr different from Rs (sender) and Rd (destination).
//        val middleRandomN = net.randomNodeExcept(sender, dest)
//
//        // Build routing rules for this flow.
//        return RoutPath(this@NetFlow) {
//            var currN = sender
//
//            // For the first part target is `middleRandomN`,
//            // for the second part is the destination node.
//            var target = middleRandomN
//            while (currN != dest) {
//                with(currN) {
//                    // Select one of the ports on `currN` that leads
//                    // to one of the shortest paths towards `target`.
//                    val ports = OSPF.selectPorts(target)
//                    assert(ports.size == 1)
//
//                    // Add entry for the current node.
//                    put(currN, ports)
//
//                    // Update `curr` to the node to which the single `Port` in `ports` is connected.
//                    currN = ports.first().txLink!!.receiverPort.owner
//
//                    // If `middleRandomN` then the first part of VAL is completed,
//                    // and we can proceed to routing from `middleRandomN`
//                    // to the actual destination node.
//                    if (currN == middleRandomN) target = dest
//                }
//            }
//        }
//    }
//
//    context(NetSimScope, VAL.Ctx)
//    private fun NetFlow.amendPath() {
//        TODO()
//    }
//
//    /**
//     * The network-aware context for this routing protocol, as [NetSimScope] context element.
//     */
//    class Ctx(
//        routingPaths: MutableMap<NetFlow, RoutPath> = mutableMapOf(),
//    ): MutableMap<NetFlow, RoutPath> by routingPaths, GlobalRoutCtx() {
//
//        companion object Key : CoroutineContext.Key<Ctx>
//
//        context(NetSimScope) override suspend fun onFlowStarted() {
//            TODO("Not yet implemented")
//        }
//
//        context(NetSimScope) override suspend fun onFlowStopped() {
//            TODO("Not yet implemented")
//        }
//
//        context(NetSimScope) override suspend fun onNodeAdded() {
//            TODO("Not yet implemented")
//        }
//
//        context(NetSimScope) override suspend fun onNodeRemoved() {
//            TODO("Not yet implemented")
//        }
//
//        override suspend fun invalidate() {
//            TODO("Not yet implemented")
//        }
//
//        override suspend fun validate() {
//            TODO("Not yet implemented")
//        }
//    }
//}
