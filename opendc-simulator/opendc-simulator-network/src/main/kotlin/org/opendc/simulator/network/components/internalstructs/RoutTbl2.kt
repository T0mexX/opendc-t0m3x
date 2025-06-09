package org.opendc.simulator.network.components.internalstructs

import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressTrie
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.RWLock

internal class RoutTbl2(private val owner: Node<*>) {
    /**
     * TODO
     * Handled externally to decide whether a new version of the table has been shared.
     */
    internal var shared: Boolean = true
//    /**
//     * Routing information for destination ips that **are not** within the same subnet as this.
//     */
//    private val outSubnetRout = mutableMapOf<IPv4Address, RoutTblEntry>()
//
//    /**
//     * Routing information for destination ips that **are** within the same subnet as this.
//     */
//    private val inSubnetRout = mutableMapOf<IPv4Address, RoutTblEntry>()

    /**
     * TODO
     */
    private val addrRout = mutableMapOf<IPv4Address, RoutTblEntry>()

    /**
     * The current routing vector of this node to be shared with others.
     */
    internal var routVect = this.RoutVect(owner = owner)
        private set


    /**
     * TODO
     */
    private val trie: IPv4AddressTrie = IPv4AddressTrie()

    internal fun getPossiblePathsTo(nId: NodeId): Collection<RoutTblPath> {
        val destAddr = trie.longestPrefixMatch(IPv4Address(nId.value.toInt()))
        return addrRout[destAddr]?.values ?: emptyList()
    }

    /**
     * TODO
     * @param n Adjacent node whose ip (or subnet) is to be registered.
     */
    context(NetSimScope)
    internal suspend fun registerAdjN(n: Node<*>, destAddr: IPv4Address) {

        val e: RoutTblEntry
        val p: RoutTblPath

        // If no previous path to address `destAddr` (either ip or subnet) was available.
        if (destAddr !in addrRout) {
            assert(destAddr !in trie)

            addrRout[destAddr] = RoutTblEntry(
                destAddr = destAddr,
            ).also { e = it }
            trie.addNode(destAddr)

            // If a previous path to `ip` was available through a different next hop.
        } else e = addrRout[destAddr]!!

        // Add the direct path of length 1 among the possible paths
        // to `destAddr` (either ip or subnet) with `n` as the next hop.
        e[n.ip] = RoutTblPath(destAddr = destAddr, nextHop = n, distance = 1).also { p = it }

        routVect.withWLock {
            // Independently of the previous available path, the new direct path of
            // distance 1 is at least as short as the previous shortest one.

            // Register the new path as the shortest to ip `ip`.
            routVect[destAddr] = p
        }
    }

    context(NetSimScope)
    internal suspend fun updtWithInfoFrom(v: RoutVect): Boolean {
        assert(v.owner != owner)
        assert(v.owner.ip != owner.ip)

        // The updates to the routing vector to be applied all together.
        val updts = mutableMapOf<IPv4Address, RoutTblPath?>()

        // If at any point during merge distances change, this is set to `true`.
        var changed = false

        // The outermost subnet in which this node is in but `v.owner` is not.
        val a = this.owner.ip.outerMostSubnetNotIncludingIp(v.owner.ip)
        // The outermost subnet in which `v.owner` is in but this node is not.
        val b = v.owner.ip.outerMostSubnetNotIncludingIp(this.owner.ip)

        // If a path of length 1 to address `b` (subnet or ip) with
        // `v.owner` as next node was not already present then add.
        if (addrRout[b]?.get(v.owner.ip)?.distance != 1) {
            changed = true
            registerAdjN(v.owner, b)
        }

        v.withRLock {
            v.forEach { (destAddr, otherP) ->
                // If the destination address for this shortest path is within a
                // subnet that this node is not part of.
                // All those IPs will be handled as a single one.
                if (destAddr in b) return@forEach

                // If the destination address is a subnet containing this node.
                if (destAddr == a) return@forEach

                // If the next hop for the path to `destAddr` from `v.owner` is this node, then ignore.
                if (otherP?.nextHop == this.owner) return@forEach

                //
                // At this point `destAddr` (either ip or subnet)
                // is not contained either in `a` nor in `b`.

                val e = addrRout.getOrPut(destAddr) {
                    assert(destAddr !in trie)
                    trie.addNode(destAddr)

                    RoutTblEntry(destAddr = destAddr)
                }

                val oldP = e[v.owner.ip]

                assert(oldP != null || otherP != null)

                // New path to `destAddr` with `v.owner` as next hop.
                if (oldP == null) {
                    e[v.owner.ip] =
                        RoutTblPath(destAddr = destAddr, nextHop = v.owner, distance = otherP!!.distance + 1)

                    // Old path to `destAddr` with `v.owner` as next hop is not available anymore.
                } else if (otherP == null) {
                    e.remove(v.owner.ip)

                    // If the old path to `destAddr` with `v.owner` as next hop might have changed its distance.
                } else {
                    // No changes.
                    if (otherP.distance + 1 == oldP.distance) return@forEach

                    oldP.distance = otherP.distance + 1
                }

                // Update routing vector of this node.
                val newMin = e.values.minOrNull()
                val oldMin = routVect[destAddr]
                if (oldMin?.distance != newMin?.distance) {
                    changed = true
                    updts[destAddr] = newMin
                }
            }
        }

        routVect.withWLock {
            routVect.putAll(updts)
        }


        return changed
    }
//
//    context(NetSimScope)
//    private suspend fun registerAdjInSubnetAddr(addr: IPv4Address, owner: Node<*>) {
//        assert(ip in subnet)
//        // Assert `addr` is a direct subnet of the subnet this node is in.
//        assert(addrMngr.getSubnetOf(addr) == subnet)
//        assert(ip.isPrefixed && ip.toPrefixBlock() == subnet)
//
//        val e: RoutTblEntry
//        val p: RoutTblPath
//
//        // If no previous path to `ip` was available.
//        if (ip !in inSubnetRout) {
//            assert(ip !in trie)
//            // Add as an entry in the intra-subnet routing table.
//            inSubnetRout[ip] = RoutTblEntry(
//                destAddr = ip,
//            ).also { e = it }
//            // Add the ip to the trie.
//            trie.addNode(ip)
//
//        // If a previous path to `ip` was available through a different next hop.
//        } else e = inSubnetRout[ip]!!
//
//        // Add the direct path of length 1 among the possible paths to `ip` with `ip` itself as the next hop.
//        e[ip] = RoutTblPath(destAddr = ip, nextHop = owner, distance = 1).also { p = it }
//
//        routVect.withLock {
//            // Independently of the previous available path, the new direct path of
//            // distance 1 is at least as short as the previous shortest one.
//
//            // Register the new path as the shortest to ip `ip`.
//            routVect.inSubnetShortestPaths[ip] = p
//        }
//    }
//
//    private fun registerAdjSubnet(subnet: IPv4Address, nextHop: Node<*>) {
//        assert(subnet.isPrefixBlock)
//        assert(subnet !in)
//    }
//
//    internal fun updtWithInfoFrom(otherN: Node<*>): Boolean {
//        assert(owner isConnectedTo otherN)
//        val otherIp = otherN.ip
//
//        // If at any point during merge distances change, this is set to `true`.
//        var changed = false
//
//        fun inSubnet() {
//            assert(otherIp in subnet)
//            val otherSubnet = otherIp.toPrefixBlock()
//            assert(otherSubnet == this.subnet)
//            if (otherIp !in inSubnetRout) {
//                changed = true
//                registerAdjIp(ip = otherIp, owner = otherN)
//            }
//
//
//        }
//
//    }


    /**
     * The possible next hops to reach [destAddr] (ip or subnet),
     * with the corresponding path lengths.
     *
     * @property destAddr Can be both a subnet or a specific ip.
     */
    private data class RoutTblEntry(
        val destAddr: IPv4Address,
        private val nextHop2Path: MutableMap<IPv4Address, RoutTblPath> = mutableMapOf(),
    ) : MutableMap<IPv4Address, RoutTblPath> by nextHop2Path

    /**
     * @property destAddr Can be both a subnet or a specific ip.
     * @property nextHop The adjacent node to redirect flows to, to reach [destAddr].
     * @property distance The number of hops needed to reach [destAddr]
     * if a flow is redirected to adjacent node [nextHop].
     */
    data class RoutTblPath(
        val destAddr: IPv4Address,
        val nextHop: Node<*>,
        var distance: Int,
    ) : Comparable<RoutTblPath> {
        override fun compareTo(other: RoutTblPath): Int = this.distance - other.distance

        context(Node<*>)
        internal fun associatedLink(): Link =
            links.find { it?.receiverN === nextHop }!!
    }



    inner class RoutVect(
        val owner: Node<*>,
        private val shortestPaths: MutableMap<IPv4Address, RoutTblPath?> = mutableMapOf(),
    ) : RWLock(
        readPermits = this@RoutTbl2.owner.nPorts.takeIf { it != 0 } ?: 10
    ), MutableMap<IPv4Address, RoutTblPath?> by shortestPaths
    /**
     * TODO
     */
//    private data class RoutTblEntry(
//        // Could be subnet or ip
//        val destAddr: IPv4Address,
//        private val nextHop2Path: MutableMap<NodeId, RoutTblPath> = mutableMapOf(),
//    ): MutableMap<NodeId, RoutTblPath> by nextHop2Path

//    /**
//     * TODO
//     */
//    data class RoutTblPath(
//        // Could be subnet or ip
//        val destAddr: IPv4Address,
//        val nextHop: Node<*>,
//        var distance: Int = -1,
//    ): Comparable<RoutTblPath> {
//        override fun compareTo(other: RoutTblPath): Int = distance - other.distance
//
////        context(Node<*>)
////        internal fun associatedPort(): Port =
////            ports.find { it.connectedNode() === nextHop }!!
//    }

//    private fun IPv4Address.isInThisSubnet(): Boolean {
//        assert(this@IPv4Address.isPrefixed)
//        return this@IPv4Address.toPrefixBlock() == subnet
//    }
//
//    private fun Map<IPv4Address, RoutTblEntry>.toDistanceMap(excludeNextHop: Node<*>) {
//
//    }

    /**
     * TODO
     */
    context(NetSimScope)
    private fun IPv4Address.outerMostSubnetNotIncludingIp(other: IPv4Address): IPv4Address {
        // Assert `other` is in fact a specific ip.
        assert(other.isPrefixBlock.not())

        var prev: IPv4Address = this@IPv4Address
        addrMngr.getNestedSubnetsOfIp(this@IPv4Address).forEach { subnet ->
            if (other in subnet) return prev
            prev = subnet
        }
        return prev
    }
}






//
//package org.opendc.simulator.network.components.internalstructs
//
//import inet.ipaddr.ipv4.IPv4Address
//import inet.ipaddr.ipv4.IPv4AddressTrie
//import org.opendc.simulator.network.components.link.Link
//import org.opendc.simulator.network.components.node.Internet
//import org.opendc.simulator.network.components.node.Node
//import org.opendc.simulator.network.components.node.NodeId
//import org.opendc.simulator.network.components.node.Switch
//import org.opendc.simulator.network.simscope.NetSimScope
//import org.opendc.simulator.network.utils.RWLock
//
//internal class RoutTbl2(private val owner: Node<*>) {
//    /**
//     * TODO
//     * Handled externally to decide whether a new version of the table has been shared.
//     */
//    internal var shared: Boolean = true
////    /**
////     * Routing information for destination ips that **are not** within the same subnet as this.
////     */
////    private val outSubnetRout = mutableMapOf<IPv4Address, RoutTblEntry>()
////
////    /**
////     * Routing information for destination ips that **are** within the same subnet as this.
////     */
////    private val inSubnetRout = mutableMapOf<IPv4Address, RoutTblEntry>()
//
//    /**
//     * TODO
//     */
//    private val addrRout = mutableMapOf<IPv4Address, RoutTblEntry>()
//
//    /**
//     * The current routing vector of this node to be shared with others.
//     */
//    internal var routVect = this.RoutVect(owner = owner)
//        private set
//
//
//    /**
//     * TODO
//     */
//    private val trie: IPv4AddressTrie = IPv4AddressTrie()
//
//    /**
//     * TODO
//     */
//    internal fun getPossiblePathsTo(nId: NodeId): Collection<RoutTblPath> {
//        val destAddr = trie.longestPrefixMatch(IPv4Address(nId.value.toInt()))
//        return addrRout[destAddr]?.values ?: emptyList()
//    }
//
//    /**
//     * TODO
//     * @param n Adjacent node whose ip (or subnet) is to be registered.
//     */
//    context(NetSimScope)
//    internal suspend fun registerAdjN(n: Node<*>, destAddr: IPv4Address): Boolean {
//        // If configured to only hold routing information towards hosts, then ignore adjacent switches.
//        if (this@NetSimScope.devConfig.netConfig.includeRoutInfo2Switches.not() && n is Switch && destAddr.isPrefixBlock.not()) return false
//
//        val e: RoutTblEntry
//        val p: RoutTblPath
//
//        // If no previous path to address `destAddr` (either ip or subnet) was available.
//        if (destAddr !in addrRout) {
//            assert(destAddr !in trie)
//
//            addrRout[destAddr] = RoutTblEntry(
//                destAddr = destAddr,
//            ).also { e = it }
//            trie.addNode(destAddr)
//
//        // If a previous path to `ip` was available through a different next hop.
//        } else e = addrRout[destAddr]!!
//
//        // Add the direct path of length 1 among the possible paths
//        // to `destAddr` (either ip or subnet) with `n` as the next hop.
//        e[n.ip] = RoutTblPath(
//            destAddr = destAddr,
//            nextHop = n,
//            distance = 1,
//            addrHops = listOf(destAddr)
//        ).also { p = it }
//
//        routVect.withWLock {
//            // Independently of the previous available path, the new direct path of
//            // distance 1 is at least as short as the previous shortest one.
//
//            // Register the new path as the shortest to ip `ip`.
//            routVect[destAddr] = p
//        }
//
//        return true
//    }
//
//    context(NetSimScope)
//    internal suspend fun updtWithInfoFrom(v: RoutVect): Boolean {
//        assert(v.owner != owner)
//        assert(v.owner.ip != owner.ip)
//
//        // The updates to the routing vector to be applied all together.
//        val updts = mutableMapOf<IPv4Address, RoutTblPath?>()
//
//        // If at any point during merge distances change, this is set to `true`.
//        var changed = false
//
//        // The outermost subnet in which this node is in but `v.owner` is not.
//        val a = this.owner.ip.outerMostSubnetNotIncludingIp(v.owner.ip)
//        // The outermost subnet in which `v.owner` is in but this node is not.
//        val b = v.owner.ip.outerMostSubnetNotIncludingIp(this.owner.ip)
//
//        // If a path of length 1 to address `b` (subnet or ip) with
//        // `v.owner` as next node was not already present then add.
//        if (addrRout[b]?.get(v.owner.ip)?.distance != 1) {
//            changed = changed or registerAdjN(v.owner, b)
//        }
//
//        // Ignore paths with internet as next hop if the internet itself is not the destination.
//        // If the destination is inside the network, an intra-network option is used.
//        if (v.owner is Internet) return changed
//
//        v.withRLock {
//            v.forEach { (destAddr, otherP) ->
//                // If the destination address for this shortest path is within a
//                // subnet that this node is not part of.
//                // All those IPs will be handled as a single one.
//                if (destAddr in b) return@forEach
//
//                // If the destination address is a subnet containing this node.
//                if (destAddr == a) return@forEach
//
//
//                // If the next hop for the path to `destAddr` from `v.owner` is this node, then ignore.
////                if (otherP?.nextHop == this.owner) return@forEach
//
//                // If the path already passes through `a` (subnet or specific ip),
//                // then ignore (avoid circular paths)
////                if (otherP != null && otherP.addrHops[0].isPrefixed) {
////                    println("${otherP.addrHops}  to destination ${destAddr}, received by ${v.owner.ip} ${owner.ip} (a:${a}, b:${b})")
////                }
//                if (otherP != null && otherP.addrHops.any { a in it }) {
//                    return@forEach
//                }
//
//                //
//                // At this point `destAddr` (either ip or subnet)
//                // is not contained either in `a` nor in `b`.
//
//                val e = addrRout.getOrPut(destAddr) {
//                    assert(destAddr !in trie)
//                    trie.addNode(destAddr)
//
//                    RoutTblEntry(destAddr = destAddr)
//                }
//
//                val oldP = e[v.owner.ip]
//
//                assert(oldP != null || otherP != null)
//
//                // New path to `destAddr` with `v.owner` as next hop.
//                if (oldP == null) {
//                    e[v.owner.ip] =
//                        RoutTblPath(
//                            destAddr = destAddr,
//                            nextHop = v.owner,
//                            addrHops = buildList {
//                                // Add `b` subnet.
//                                add(b)
//                                // Keep traversed subnets that are not within `b`.
//                                addAll(otherP!!.addrHops.filterNot { it in b })
//                            },
//                            distance = otherP!!.distance + 1,
//                        )
//
//                    // Old path to `destAddr` with `v.owner` as next hop is not available anymore.
//                } else if (otherP == null) {
//                    e.remove(v.owner.ip)
//
//                    // If the old path to `destAddr` with `v.owner` as next hop might have changed its distance.
//                } else {
//                    // No changes.
//                    if (otherP.distance + 1 == oldP.distance) return@forEach
//
//                    e.replace(
//                        destAddr,
//                        oldP.copy(
//                            addrHops = buildList {
//                                // Add `b` subnet.
//                                add(b)
//                                // Keep traversed subnets that are not within `b`.
//                                addAll(otherP.addrHops.filterNot { it in b })
//                            },
//                            distance = otherP.distance + 1,
//                        ),
//                    )
//                }
//
//                // Update routing vector of this node.
//                val newMin = e.values.minOrNull()
//                val oldMin = routVect[destAddr]
//                if (oldMin?.distance != newMin?.distance) {
//                    changed = true
//                    updts[destAddr] = newMin
//                }
//            }
//        }
//
//        routVect.withWLock {
//            routVect.putAll(updts)
//        }
//
//        return changed
//    }
//
////
////    context(NetSimScope)
////    private suspend fun registerAdjInSubnetAddr(addr: IPv4Address, owner: Node<*>) {
////        assert(ip in subnet)
////        // Assert `addr` is a direct subnet of the subnet this node is in.
////        assert(addrMngr.getSubnetOf(addr) == subnet)
////        assert(ip.isPrefixed && ip.toPrefixBlock() == subnet)
////
////        val e: RoutTblEntry
////        val p: RoutTblPath
////
////        // If no previous path to `ip` was available.
////        if (ip !in inSubnetRout) {
////            assert(ip !in trie)
////            // Add as an entry in the intra-subnet routing table.
////            inSubnetRout[ip] = RoutTblEntry(
////                destAddr = ip,
////            ).also { e = it }
////            // Add the ip to the trie.
////            trie.addNode(ip)
////
////        // If a previous path to `ip` was available through a different next hop.
////        } else e = inSubnetRout[ip]!!
////
////        // Add the direct path of length 1 among the possible paths to `ip` with `ip` itself as the next hop.
////        e[ip] = RoutTblPath(destAddr = ip, nextHop = owner, distance = 1).also { p = it }
////
////        routVect.withLock {
////            // Independently of the previous available path, the new direct path of
////            // distance 1 is at least as short as the previous shortest one.
////
////            // Register the new path as the shortest to ip `ip`.
////            routVect.inSubnetShortestPaths[ip] = p
////        }
////    }
////
////    private fun registerAdjSubnet(subnet: IPv4Address, nextHop: Node<*>) {
////        assert(subnet.isPrefixBlock)
////        assert(subnet !in)
////    }
////
////    internal fun updtWithInfoFrom(otherN: Node<*>): Boolean {
////        assert(owner isConnectedTo otherN)
////        val otherIp = otherN.ip
////
////        // If at any point during merge distances change, this is set to `true`.
////        var changed = false
////
////        fun inSubnet() {
////            assert(otherIp in subnet)
////            val otherSubnet = otherIp.toPrefixBlock()
////            assert(otherSubnet == this.subnet)
////            if (otherIp !in inSubnetRout) {
////                changed = true
////                registerAdjIp(ip = otherIp, owner = otherN)
////            }
////
////
////        }
////
////    }
//
//
//    /**
//     * The possible next hops to reach [destAddr] (ip or subnet),
//     * with the corresponding path lengths.
//     *
//     * @property destAddr Can be both a subnet or a specific ip.
//     */
//    private data class RoutTblEntry(
//        val destAddr: IPv4Address,
//        private val nextHop2Path: MutableMap<IPv4Address, RoutTblPath> = mutableMapOf(),
//    ) : MutableMap<IPv4Address, RoutTblPath> by nextHop2Path
//
//    /**
//     * @property destAddr Can be both a subnet or a specific ip.
//     * @property nextHop The adjacent node to redirect flows to, to reach [destAddr].
//     * @property distance The number of hops needed to reach [destAddr]
//     * if a flow is redirected to adjacent node [nextHop].
//     */
//    data class RoutTblPath(
//        val destAddr: IPv4Address,
//        val nextHop: Node<*>,
//        val addrHops: List<IPv4Address>,
//        val distance: Int,
//    ) : Comparable<RoutTblPath> {
//        override fun compareTo(other: RoutTblPath): Int = this.distance - other.distance
//
//        context(Node<*>)
//        internal fun associatedLink(): Link =
//            links.find { it?.receiverN === nextHop }!!
//    }
//
//
//
//    inner class RoutVect(
//        val owner: Node<*>,
//        private val shortestPaths: MutableMap<IPv4Address, RoutTblPath?> = mutableMapOf(),
//    ) : RWLock(
//        readPermits = this@RoutTbl2.owner.nPorts.takeIf { it != 0 } ?: 10
//    ), MutableMap<IPv4Address, RoutTblPath?> by shortestPaths
//    /**
//     * TODO
//     */
////    private data class RoutTblEntry(
////        // Could be subnet or ip
////        val destAddr: IPv4Address,
////        private val nextHop2Path: MutableMap<NodeId, RoutTblPath> = mutableMapOf(),
////    ): MutableMap<NodeId, RoutTblPath> by nextHop2Path
//
////    /**
////     * TODO
////     */
////    data class RoutTblPath(
////        // Could be subnet or ip
////        val destAddr: IPv4Address,
////        val nextHop: Node<*>,
////        var distance: Int = -1,
////    ): Comparable<RoutTblPath> {
////        override fun compareTo(other: RoutTblPath): Int = distance - other.distance
////
//////        context(Node<*>)
//////        internal fun associatedPort(): Port =
//////            ports.find { it.connectedNode() === nextHop }!!
////    }
//
////    private fun IPv4Address.isInThisSubnet(): Boolean {
////        assert(this@IPv4Address.isPrefixed)
////        return this@IPv4Address.toPrefixBlock() == subnet
////    }
////
////    private fun Map<IPv4Address, RoutTblEntry>.toDistanceMap(excludeNextHop: Node<*>) {
////
////    }
//
//    /**
//     * TODO
//     */
//    context(NetSimScope)
//    private fun IPv4Address.outerMostSubnetNotIncludingIp(ip: IPv4Address): IPv4Address {
//        // Assert `other` is in fact a specific ip.
//        assert(ip.isPrefixBlock.not())
//
//        var prev: IPv4Address = this@IPv4Address
//        addrMngr.getNestedSubnetsOfIp(this@IPv4Address).forEach { subnet ->
//            if (ip in subnet) return prev
//            prev = subnet
//        }
//        return prev
//    }
//}
