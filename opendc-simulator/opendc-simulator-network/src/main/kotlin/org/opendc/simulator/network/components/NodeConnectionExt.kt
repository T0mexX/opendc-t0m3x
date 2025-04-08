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

package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.RoutingVect
import org.opendc.simulator.network.components.node.Node

//private val log by Unit.logger("NodeConnectionExt")
//
///**
// * @return `true` on success, `false` otherwise.
// */
//internal suspend fun Node.connect(
//    other: Node,
//    linkBW: DataRate? = null,
//): Boolean {
//
//    val freePort: Port =
//        getFreePort()
//            ?: throw RuntimeException("unable to connect, max num of connected nodesById reached ($nPorts).")
//
//    val otherPort: Port =
//        other.accept(this)
//            ?: throw RuntimeException("unable to connect, node $other refused connection")
//
//
////        portToNode[other.id] = freePort
//
//        freePort.connect(otherPort, linkBW = linkBW)
//
//        val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.getVect(), vectOwner = this)
//        routingTable.mergeRoutingVector(otherVect, vectOwner = other)
//        shareRoutingVect(except = listOf(other))
//
//        with(flowHandler) { updtAllRouts() }
//        updateAllFlows()
//
//        true
//    }
//
///**
// * Accepts a connection request from `other` [Node],
// * second part of method [connect].
// * @see connect
// * @param[other]    node that is requesting to connect.
// */
//private suspend fun Node.accept(other: Node): Port? =
//    updtChl.whileUpdtProcessingLocked {
//        val freePort: Port =
//            getFreePort()
//                ?: let {
//                    log.error("Unable to accept connection, maximum number of connected nodesById reached ($numOfPorts).")
//                    return@whileUpdtProcessingLocked null
//                }
//
//        portToNode[other.id] = freePort
//
//        freePort
//    }
//
//internal suspend fun Node.disconnect(other: Node) = this.disconnect(other = other, notifyOther = true)
//
///**
// * Disconnects all ports of ***this*** node.
// */
//internal suspend fun Node.disconnectAll() {
//    portToNode.mapNotNull { (_, port) ->
//        port.otherEndNode
//    }.forEach { disconnect(it, notifyOther = true) }
//}
//
///**
// * Disconnects ***this*** from [other].
// * @return `true` on success, `false` otherwise.
// */
//private suspend fun Node.disconnect(
//    other: Node,
//    notifyOther: Boolean,
//): Boolean =
//    updtChl.whileUpdtProcessingLocked {
//        val portToOther: Port =
//            portToNode[other.id]
//                ?: return@whileUpdtProcessingLocked log.withWarn(
//                    false,
//                    "unable to disconnect $this from $other, nodesById are not connected",
//                )
//
//        if (notifyOther) {
//            other.disconnect(this, notifyOther = false)
//        }
//
//        portToOther.disconnect()
//
//        routingTable.removeNextHop(other)
//        portToNode.remove(other.id)
//
//        shareRoutingVect(exchange = true)
//
//        with(flowHandler) { updtAllRouts() }
//        updateAllFlows()
//
//        true
//    }
//
///**
// * Returns an available port if exists, else null.
// */
//private fun Node.getFreePort(): Port? = ports.firstOrNull { !it.isConnected }

/**
 * Merges [routVect] in the [this.routingTable], updating flowsById
 * and sharing its updated routing vector if needed.
 * @return its own routing vector.
 */
internal suspend fun Node<*>.exchangeRoutVect(
    routVect: RoutingVect,
    vectOwner: Node<*>,
): RoutingVect {
    routingTable.mergeRoutingVector(routVect, vectOwner)

    if (!routingTable.isTableChanged) return routingTable.getVect()
//    with(flowHandler) { updtAllRouts() }

//    updateAllFlows()
//    TODO()

    if (!routingTable.isVectChanged) return routingTable.getVect()
    shareRoutingVect(except = listOf(vectOwner))

    return routingTable.getVect()
}

/**
 * Shares ***this*** routing vector with all adjacent nodesById, except those in [except].
 * @param[except]       the list of nodesById to which not share the vector with.
 * @param[exchange]     determines if ***this*** should receive and merge other's vectors as well.
 * If so, the process continues until one iteration of the function is completed without that ***this*** routing vector changes.
 */
internal tailrec suspend fun Node<*>.shareRoutingVect(
    except: Collection<Node<*>> = listOf(),
    exchange: Boolean = false,
) {
//    TODO()

    ports.forEach { p ->
        // If port not connected then skip.
        p.txLink ?: return@forEach

        val adjN = p.txLink!!.receiverPort.owner
        // If the adjacent node is in the `except` list, then skip.
        if (adjN in except) return@forEach

        val otherVct = adjN.exchangeRoutVect(routingTable.getVect(), vectOwner = this)
        if (exchange) {
            routingTable.mergeRoutingVector(otherVct, vectOwner = adjN)

            // If no changes to the routing table, then go to the next port.
            if (routingTable.isTableChanged.not()) return@forEach

            // TODO: remove old code.
            // with(flowHandler) { updtAllRouts() }
            // updateAllFlows()
            if (routingTable.isVectChanged.not()) return@forEach
            @Suppress("NON_TAIL_RECURSIVE_CALL")
            shareRoutingVect(except = listOf(adjN), exchange = true)
        }
    }
}
