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

package org.opendc.simulator.network.repl.synthetictraffic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.tongfei.progressbar.ProgressBar
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NETWORK_JSON
import org.opendc.simulator.network.utils.SerialSTraffic
import org.opendc.simulator.network.utils.increaseMax
import kotlin.math.log2

/**
 * TODO
 */
@Serializable
internal abstract class SyntheticTraffic<in T : Network<*>> : SerialSTraffic {
    /**
     * Needs to be implemented if `getDest(Int, Map<Int, HostNode>)` is not.
     */
    context(NetSimScope)
    protected open fun getDest(
        src: Int,
        nNodes: Int,
    ): Int? = throw UnsupportedOperationException()

    /**
     * Default it invokes [getDest].
     */
    context(NetSimScope)
    protected open fun getDest(
        srcTmpId: Int,
        tmpIdMap: Map<Int, Terminal>,
    ): Terminal? =
        getDest(srcTmpId, tmpIdMap.size)?.let { destTmpId ->
            tmpIdMap[destTmpId]!!
        }

    /**
     * TODO
     * @param demandMapping Maps each [Terminal] to their new flow demand.
     */
    context(NetSimScope, ProgressBar)
    open suspend fun startSyntheticFlows(
        net: Network<*>,
        demandMapping: (Terminal) -> DataRate,
    ) {
        val netSpecs = net.specs

        // All the hosts in the network sorted by their ip/id (id = int representation of ip).
        // The network should have been built in such a way that ids are representative of the vicinity of nodes.
        val hosts = net.getNodesById<Terminal>().values.sortedBy { it.ip.toNId() }

        // Increase the number of ticks for the current
        // progress bar by the number of flows to be started.
        this@ProgressBar.increaseMax(netSpecs.N_.toLong())

        // Since ids are not necessarily 0 to N-1,
        // they are mapped to temporary ids 0 to N-1 to perform bit-permutations if necessary.
        val map =
            hosts.mapIndexed { idx, h ->
                idx to h
            }.toMap()

        // Start all flows.
        map.forEach { (tmpId, srcN) ->
            val destN = getDest(srcTmpId = tmpId, map)
            destN ?: return@forEach

            val newF =
                devConfig.netFlowConfig.version(
                    srcId = srcN.id,
                    destId = destN.id,
                    dmnd = demandMapping(srcN),
                )

            net.startFlow(newF)
            this@ProgressBar.step()
        }
    }

    protected fun requirePow2Hosts(nNodes: Int) {
        val lg: Int = log2(nNodes.toDouble()).toInt()

        require(1 shl lg == nNodes) {
            "Error: traffic pattern requires the number of hosts to be a power of 2"
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromString(str: String): SyntheticTraffic<Network<*>> =
            NETWORK_JSON.decodeFromString<SerialSTraffic>(str) as SyntheticTraffic<Network<*>>
    }
}

@Serializable
@SerialName("random")
internal data object STrafficRandom : SyntheticTraffic<Network<*>>() {
    context(NetSimScope)
    override fun getDest(
        src: Int,
        nNodes: Int,
    ): Int {
        var dest: Int
        do {
            dest = this@NetSimScope.config.random.nextInt(nNodes)
        } while (dest == src)

        return dest
    }
}

/**
 * TODO
 */
@Serializable
@SerialName("bit-complement")
internal data object STrafficBitComplement : SyntheticTraffic<Network<*>>() {
    context(NetSimScope)
    override fun getDest(
        src: Int,
        nNodes: Int,
    ): Int {
        requirePow2Hosts(nNodes)
        val mask: Int = nNodes - 1
        return (src.inv()) and mask
    }
}

/**
 * TODO
 */
@Serializable
@SerialName("bit-shuffle")
internal data object STrafficBitShuffle : SyntheticTraffic<Network<*>>() {
    context(NetSimScope)
    override fun getDest(
        src: Int,
        nNodes: Int,
    ): Int? {
        requirePow2Hosts(nNodes)
        val lg: Int = log2(nNodes.toDouble()).toInt()

        val dest = ((src shl 1) and (nNodes - 1)) or ((src shr (lg - 1)) and 0x1)

        // Avoid src == dest.
        return dest.takeUnless { src == it }
    }
}

/**
 * TODO
 */
@Serializable
@SerialName("bit-reversal")
internal data object STrafficBitReversal : SyntheticTraffic<Network<*>>() {
    context(NetSimScope)
    override fun getDest(
        src: Int,
        nNodes: Int,
    ): Int? {
        requirePow2Hosts(nNodes)
        val lg: Int = log2(nNodes.toDouble()).toInt()
        var dest: Int

        // Could be done in n O(log log total_nodes)
        dest = 0
        for (b in 0..<lg) {
            dest = dest or (((src shr b) and 0x1) shl (lg - b - 1))
        }

        // For palindrome bit representations.
        if (src == dest) return null

        return dest
    }
}

/**
 * TODO
 */
@Serializable
@SerialName("bit-transpose")
internal data object STrafficBitTranspose : SyntheticTraffic<Network<*>>() {
    context(NetSimScope)
    override fun getDest(
        src: Int,
        nNodes: Int,
    ): Int? {
        val lg: Int = log2(nNodes.toDouble()).toInt()
        val loMask = (1 shl (lg / 2)) - 1
        val hiMask = loMask shl (lg / 2)

        val dest =
            ((src shr (lg / 2)) and loMask) or
                ((src shl (lg / 2)) and hiMask)

        return dest.takeUnless { src == it }
    }
}

@Serializable
@SerialName("random-perm")
internal class STrafficRandomPerm : SyntheticTraffic<Network<*>>() {
    @Transient
    private lateinit var permMap: IntArray

    context(NetSimScope)
    override fun getDest(
        src: Int,
        nNodes: Int,
    ): Int? {
        if (::permMap.isInitialized.not()) generateRandomPermutation(nNodes)

        return permMap[src].takeUnless { src == it }
    }

    context(NetSimScope)
    private fun generateRandomPermutation(nNodes: Int) {
        permMap = IntArray(nNodes) { -1 }
        val rand = this@NetSimScope.config.random

        permMap.forEachIndexed { dest, _ ->
            val ind = rand.nextInt(nNodes - dest)

            var j = 0
            var cnt = 0
            while (cnt < ind || permMap[j] != -1) {
                if (permMap[j] == -1) ++cnt
                ++j

                check(j < nNodes)
            }

            permMap[j] = dest
        }
    }
}

/**
 * TODO
 */
@Serializable
@SerialName("uniform")
internal class STrafficUniform : SyntheticTraffic<Network<*>>() {
    context(NetSimScope, ProgressBar)
    override suspend fun startSyntheticFlows(
        net: Network<*>,
        demandMapping: (Terminal) -> DataRate,
    ) {
        val netSpecs = net.specs

        // The number of flows to start is N_ * (N-1) since
        // every host sends traffic to every other host.
        this@ProgressBar.increaseMax(netSpecs.N_ * (netSpecs.N_ - 1).toLong())

        val hosts = net.getNodesById<Terminal>().values

        hosts.forEach outer@{ src ->
            hosts.forEach inner@{ dest ->
                if (src === dest) return@inner

                val newF =
                    devConfig.netFlowConfig.version(
                        srcId = src.id,
                        destId = dest.id,
                        dmnd = demandMapping(src) / (hosts.size - 1),
                    )

                net.startFlow(newF)
                this@ProgressBar.step()
            }
        }
    }
}
