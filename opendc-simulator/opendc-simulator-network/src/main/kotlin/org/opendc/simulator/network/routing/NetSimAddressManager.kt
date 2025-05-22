package org.opendc.simulator.network.routing

import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressNetwork
import inet.ipaddr.ipv4.IPv4AddressNetwork.IPv4AddressCreator
import inet.ipaddr.ipv4.IPv4AddressTrie
import inet.ipaddr.ipv4.IPv4AddressTrie.IPv4TrieNode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.log2

private typealias TNode = IPv4TrieNode

internal class NetSimAddressManager: AbstractCoroutineContextElement(Key) {

    private val mtx = Mutex()
    private val net = IPv4AddressNetwork()
    // TODO: if normal `IPv4Address` constructors can be used without fucking up
    //  the link to the network (if it is even useful), then they are much better
    //  than using the creator, since with the creator you cannot initialize from `Int`.
    private val creator: IPv4AddressCreator = net.addressCreator

    private val trie = IPv4AddressTrie()

    internal fun fmt(): String = trie.toString()

    /**
     * @param of The parent subnet (if any) the new subnet needs to be part of. It defaults to "0.0.0.0/0".
     *
     * Requirements:
     * - It needs to be a prefix block.
     * - It needs to be a subnet already allocated in the [NetSimScope].
     * @param nIps The number of IPs needed in the new subnet,
     * the returned subnet will be the smallest possible IPv4
     * subnet (as a prefix block) that can contain at least [nIps] addresses.
     * @return The new subnet as a *prefix block*.
     */
    internal suspend fun getNewSubNet(
        of: IPv4Address? = null,
        nIps: Int
    ): IPv4Address = mtx.withLock {
        assert(of == null || of.isIPv4 && of.isPrefixBlock)
        assert(of == null || of in trie)
        assert(nIps > 0)

        // The largest prefix length that can contain nIps ips
        val targetPrefixLength: Int = IPv4Address.BIT_COUNT - ceil(log2(nIps.toDouble())).toInt()

        // The trie node corresponding to prefix block (subnet) `of`.
        val ofN = of?.let { trie.getAddedNode(of) } ?: trie.root

        ofN.gaps().forEach { blk ->
            if (blk.prefixLength > targetPrefixLength) return@forEach

            // The new prefix block (subnet).
            val newBlk = creator.createAddress(blk.bytes, targetPrefixLength).toPrefixBlock()
            assert(trie.add(newBlk))

            return newBlk
        }

        error("Unable to allocate subnet")
    }

    // TODO: maybe change to used a counter for improved performance.
    /**
     * @param subNet The subnet to search an unused ip in.
     * @return An unclaimed ip address that is part of subnet [subNet] but not of any subnets of [subNet].
     */
    internal suspend fun getNewIp(
        subNet: IPv4Address? = null,
    ): IPv4Address = mtx.withLock {
        // The trie node.
        val n = subNet?.let { trie.getAddedNode(subNet) } ?: trie.root

        n.gaps().forEach { blk ->
            val newIp = creator.createAddress(blk.bytes)
            assert(newIp !in trie)
            assert(trie.add(newIp))

            return newIp
        }

        error("Unable to allocate ip")
    }

    /**
     * @param addr The addr whose most specific subnet is to be found.
     * The outer subnet is returned even if the parameter is a subnet itself.
     * @return The longest matching subnet larger than [addr].
     */
    internal suspend fun getSubnetOf(addr: IPv4Address): IPv4Address = mtx.withLock {
        trie.getAddedNode(addr).addedParent().key
    }

    internal suspend fun getMyRoutTrie(addr: IPv4Address): IPv4AddressTrie = mtx.withLock {
        assert(addr in trie)
        val myTrie = trie.clone()
        val myN = myTrie.getAddedNode(addr)

//        // The node corresponding to the most specific subnet `addr` is in or `addr` itself if it is a subnet.
//        val mySubnetN = let {
//            if (addr.isPrefixBlock) myN
//            else myN.addedParent()
//        }

        //
        // Remove all subnets that are not direct children of any of the nodes traversed to reach the root.
        var prev = myN
        do {
            val curr = prev.addedParent()
            curr.addedDirectChildren().forEach { child ->
                if (child == prev) return@forEach
                // Removes also child.key.
                curr.removeElementsContainedBy(child.key)
                // Re-add child.key.
                myTrie.add(child.key)
            }

            prev = curr
        } while (prev != myTrie.root)


        return myTrie
    }


    /**
     * TODO
     */
    private fun TNode.gaps(): Sequence<IPv4Address> = sequence {
        val n = this@gaps


        var expectedStart: IPv4Address = n.key.lower
        n.nodeIterator(true).forEach { child ->
            if (child.addedParent() !== n) return@forEach

            val actualStart = child.key.lower
            // There is no gap between the children.
            if (actualStart.intValue() == expectedStart.intValue()) {
                val upperIntValue = child.key.upper.intValue()
                expectedStart = creator.createAddress(IPv4Address(upperIntValue + 1).bytes, child.key.prefixLength)
                return@forEach
            }

            val gapEnd = creator.createAddress(IPv4Address(actualStart.intValue() - 1).bytes)

            // The collection of prefix blocks (subnets) that span the gap.
            val span = expectedStart.spanWithPrefixBlocks(gapEnd)
            yieldAll(span.toList())
        }

        // If there is a remaining gap at the end.
        if (expectedStart.intValue() - 1 != n.key.upper.intValue() || expectedStart.intValue() == 0) {

            val span = expectedStart.spanWithPrefixBlocks(n.key.upper)
            yieldAll(span.toList())
        }
    }

    /**
     * TODO
     */
    private fun TNode.addedParent(): TNode {
        var curr = this

        do {
            curr = curr.parent
        } while (curr.isAdded.not() && curr != trie.root)

        return curr
    }

    /**
     * TODO
     */
    private fun TNode.addedDirectChildren(): Collection<TNode>  {
        val n = this@addedDirectChildren
        return buildList {
            n.allNodeIterator(true).forEach { child ->
                if (child.isAdded.not()) return@forEach
                if (child.addedParent() !== n) return@forEach

                add(child)
            }
        }
    }

    companion object Key : CoroutineContext.Key<NetSimAddressManager>
}
