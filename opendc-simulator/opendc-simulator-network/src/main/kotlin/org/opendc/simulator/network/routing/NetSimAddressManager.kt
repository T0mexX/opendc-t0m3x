package org.opendc.simulator.network.routing

import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressNetwork
import inet.ipaddr.ipv4.IPv4AddressNetwork.IPv4AddressCreator
import inet.ipaddr.ipv4.IPv4AddressTrie
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.log2

internal class NetSimAddressManager: AbstractCoroutineContextElement(Key) {
    private val mtx = Mutex()
    private val net = IPv4AddressNetwork()
    // TODO: if normal `IPv4Address` constructors can be used without fucking up
    //  the link to the network (if it is even useful), then they are much better
    //  than using the creator, since with the creator you cannot initialize from `Int`.
    private val creator: IPv4AddressCreator = net.addressCreator

    private val trie = IPv4AddressTrie()

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
//        assert(ofN.isAdded || ofN.isRoot)

        ofN.gaps().forEach { blk ->
            if (blk.prefixLength > targetPrefixLength) return@forEach

            // The new prefix block (subnet).
            val newBlk = creator.createAddress(blk.bytes, targetPrefixLength).toPrefixBlock()
            assert(trie.add(newBlk))
            println(trie)

            return newBlk
        }

        error("Unable to allocate subnet")

//        ofN.allNodeIterator(true).forEach { subNode ->
//            // The subnet is taken.
//            if (subNode.isAdded && subNode.isRoot.not()) return@forEach
//
//            if (subNode.parent !== ofN && subNode !== ) return@forEach
//
//            // The subnet cannot contain enough IPs.
//            if (subNode.key.prefixLength > targetPrefixLength) return@forEach
//
//            val newSubNetBlk = creator.createAddress(subNode.key.bytes, targetPrefixLength).toPrefixBlock()
//
//            // Add the new prefix block to the trie.
//            assert(trie.add(newSubNetBlk))
//
//            trie.getNode(newSubNetBlk)
//            println(trie)
//            return newSubNetBlk
//        }

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
            assert(trie.contains(newIp).not())
            assert(trie.add(newIp))
            println(newIp)
            println(trie)

            return newIp
        }

        error("Unable to allocate ip")
//        var expectedStart: IPv4Address = n.key.lower
//        n.nodeIterator(true).forEach { child ->
//            // There is no gap between the children.
//            if (child.key.lower != expectedStart) {
//                val upperIntValue = child.key.upper.intValue()
//                expectedStart = creator.createAddress(IPv4Address(upperIntValue + 1).bytes, child.key.prefixLength)
//                return@forEach
//            }
//            // The new unclaimed ip address part of subnet `n` but not any subnets of `n`.
//            trie.addNode(expectedStart)
//
//            return expectedStart
//        }

    }

//
//    inner class NetSimTrieNode(
//        internal val ipDispenser: SubNetIpDispenser,
//    ) : IPv4AddressTrie.IPv4TrieNode() {
//
//        inner class SubNetIpDispenser(
//            private val subnet: IPv4Address,
//        ) {
//            internal var next: Int = 0
//                private set
//                get() {
//                    // Require that the
//                    require(field < subnet.count.toInt())
//
//                    return field++
//                }
//
//            init {
//                assert(subnet.isIPv4 && subnet.isPrefixBlock)
//            }
//        }
//    }

    /**
     * TODO
     */
    private fun IPv4AddressTrie.IPv4TrieNode.gaps(): Sequence<IPv4Address> = sequence {
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

    private fun IPv4AddressTrie.IPv4TrieNode.addedParent(): IPv4AddressTrie.IPv4TrieNode {
        var curr = this

        do {
            curr = curr.parent
        } while (curr.isAdded.not() && curr !== trie.root)

        return curr
    }

    companion object Key : CoroutineContext.Key<NetSimAddressManager>
}
