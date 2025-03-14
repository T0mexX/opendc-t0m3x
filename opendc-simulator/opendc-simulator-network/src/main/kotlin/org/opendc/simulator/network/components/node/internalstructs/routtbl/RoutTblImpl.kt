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

package org.opendc.simulator.network.components.node.internalstructs.routtbl

import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressTrie
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.NetCoOwner
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.RWLock

/**
 * A routing table implementation for a specific [Node].
 *
 * This table is owned by a single node and updated through routing information shared by neighboring nodes.
 * It is responsible for:
 * - Tracking multiple possible paths to each destination address (IP or subnet),
 * - Maintaining a routing vector with the currently shortest known paths,
 * - Performing longest prefix matches using a trie structure,
 * - Reacting to updates from neighbors and adjusting the table accordingly.
 *
 * @property owner The [Node] that owns this routing table.
 */
@NetCoOwner(owner = NetCo.NODE)
internal class RoutTblImpl(private val owner: Node<*>) {
    /**
     * Indicates whether the routing table has been shared with other nodes since its last update.
     * This flag is typically used by the owner's coroutine.
     */
    internal var shared: Boolean = true

    /**
     * Maps destination addresses (IP or subnet) to a set of possible routing paths.
     */
    private val addrRout = mutableMapOf<IPv4Address, RoutTblEntry>()

    /**
     * The owner's current view of the shortest path to each known destination.
     */
    internal var routVect = this.RoutVect(owner = owner)
        private set

    /**
     * A prefix trie for efficient longest-prefix matching on IP addresses.
     */
    private val trie: IPv4AddressTrie = IPv4AddressTrie()

    /**
     * Retrieves all known paths to a given node ID.
     *
     * @param nId The ID of the node to search routes for.
     * @return A collection of all known paths to [nId], or an empty list if none found.
     */
    internal fun getPossiblePathsTo(nId: NodeId): Collection<RoutTblPath> {
        val destAddr = trie.longestPrefixMatch(IPv4Address(nId.value.toInt()))
        return addrRout[destAddr]?.values ?: emptyList()
    }

    /**
     * @param n Adjacent node whose ip (or subnet) is to be registered.
     * @param destAddr The outermost subnet not including [Node.ip] of [owner],
     * corresponding to the destination address in the (possible) new table entry.
     *
     * @return `true` if the routing vector was changed; `false` otherwise.
     */
    context(NetSimScope)
    private suspend fun registerAdjN(
        n: Node<*>,
        destAddr: IPv4Address,
    ): Boolean {
        // If configured to only hold routing information towards hosts, then ignore adjacent switches.
        if (devConfig.netConfig.swRout.not() && n is Switch && destAddr.isPrefixBlock.not()) return false

        val e: RoutTblEntry
        val p: RoutTblPath

        // If no previous path to address `destAddr` (either ip or subnet) was available.
        if (destAddr !in addrRout) {
            assert(destAddr !in trie)

            addrRout[destAddr] =
                RoutTblEntry(
                    destAddr = destAddr,
                ).also { e = it }
            trie.addNode(destAddr)

            // If a previous path to `ip` was available through a different next hop.
        } else {
            e = addrRout[destAddr]!!
        }

        // Add the direct path of length 1 among the possible paths
        // to `destAddr` (either ip or subnet) with `n` as the next hop.
        e[n.ip] = RoutTblPath(destAddr = destAddr, nextHop = n, distance = 1).also { p = it }

        routVect.withWLock {
            // Independently of the previous available path, the new direct path of
            // distance 1 is at least as short as the previous shortest one.

            // Register the new path as the shortest to ip `ip`.
            routVect[destAddr] = p
        }

        return true
    }

    /**
     * Updates this routing table using another node's shared routing vector.
     *
     * @param v The routing vector received from another node.
     * @return `true` if the routing vector was changed; `false` otherwise.
     */
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
            changed = changed or registerAdjN(v.owner, b)
        }

        // Ignore paths with internet as next hop if the internet itself is not the destination.
        // If the destination is inside the network, an intra-network option is used.
        if (v.owner is Internet) return changed

        v.withRLock {
            v.forEach { (destAddr, otherP) ->
                changed = changed or considerPath(otherP, destAddr, a, b, updts, v.owner)
            }
        }

        routVect.withWLock {
            routVect.putAll(updts)
        }

        return changed
    }

    /**
     * Considers a single path shared by [otherN] to potentially update the routing table.
     * @return `true` if the routing vector was changed, `false` otherwise.
     */
    private fun considerPath(
        otherP: RoutTblPath?,
        destAddr: IPv4Address,
        a: IPv4Address,
        b: IPv4Address,
        updts: MutableMap<IPv4Address, RoutTblPath?>,
        otherN: Node<*>,
    ): Boolean {
        // If the destination address for this shortest path is within a
        // subnet that this node is not part of.
        // All those IPs will be handled as a single one.
        if (destAddr in b) return false

        // If the destination address is a subnet containing this node.
        if (destAddr == a) return false

        // If the next hop for the path to `destAddr` from `v.owner` is this node, then ignore.
        if (otherP?.nextHop == this.owner) return false

        //
        // At this point `destAddr` (either ip or subnet)
        // is not contained either in `a` nor in `b`.

        val e =
            addrRout.getOrPut(destAddr) {
                assert(destAddr !in trie)
                trie.addNode(destAddr)

                RoutTblEntry(destAddr = destAddr)
            }

        val oldP = e[otherN.ip]

        assert(oldP != null || otherP != null)

        // New path to `destAddr` with `v.owner` as next hop.
        if (oldP == null) {
            e[otherN.ip] =
                RoutTblPath(destAddr = destAddr, nextHop = otherN, distance = otherP!!.distance + 1)

            // Old path to `destAddr` with `v.owner` as next hop is not available anymore.
        } else if (otherP == null) {
            e.remove(otherN.ip)

            // If the old path to `destAddr` with `v.owner` as next hop might have changed its distance.
        } else {
            // No changes.
            if (otherP.distance + 1 == oldP.distance) return false

            oldP.distance = otherP.distance + 1
        }

        // Update routing vector of this node.
        val newMin = e.values.minOrNull()
        val oldMin = routVect[destAddr]
        if (oldMin?.distance != newMin?.distance) {
            updts[destAddr] = newMin
            return true
        }

        return false
    }

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
        internal fun associatedLink(): Link = links.find { it?.receiverN === nextHop }!!
    }

    /**
     * A wrapper around a routing vector, maintaining shortest paths known to the owner node.
     *
     * Supports suspending locking for concurrent reads/writes in coroutine environments,
     * avoiding making a new copy every time it is shared.
     *
     * @property owner The node that owns this vector.
     */
    inner class RoutVect(
        val owner: Node<*>,
        private val shortestPaths: MutableMap<IPv4Address, RoutTblPath?> = mutableMapOf(),
    ) : RWLock(
            readPermits = this@RoutTblImpl.owner.nPorts.takeIf { it != 0 } ?: 10,
        ),
        MutableMap<IPv4Address, RoutTblPath?> by shortestPaths

    /**
     * @return the outermost subnet that includes [this] ip but not [other] ip.
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
