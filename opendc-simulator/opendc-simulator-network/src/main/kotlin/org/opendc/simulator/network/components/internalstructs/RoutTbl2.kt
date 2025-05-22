package org.opendc.simulator.network.components.internalstructs

import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressTrie
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.port.Port

internal class RoutTbl2 {
    private val trie: IPv4AddressTrie = IPv4AddressTrie()

    private val prefixToPaths = mutableMapOf<IPv4Address, RoutTblEntry>()

    private val map = mutableMapOf<Port, IPv4Address>()


//    context()

    /**
     * TODO
     */
    private data class RoutTblEntry(
        // Could be subnet or ip
        val destAddr: IPv4Address,
        private val nextHop2Path: MutableMap<NodeId, RoutTblPath> = mutableMapOf(),
    ): MutableMap<NodeId, RoutTblPath> by nextHop2Path

    /**
     * TODO
     */
    data class RoutTblPath(
        // Could be subnet or ip
        val destAddr: IPv4Address,
        val nextHop: Node<*>,
        var distance: Int = -1,
    ): Comparable<RoutTblPath> {
        override fun compareTo(other: RoutTblPath): Int = distance - other.distance

//        context(Node<*>)
//        internal fun associatedPort(): Port =
//            ports.find { it.connectedNode() === nextHop }!!
    }
}
