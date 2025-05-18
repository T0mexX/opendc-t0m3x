package org.opendc.simulator.network.repl.synthetic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2

/**
 * Bit-reversal synthetic traffic pattern.
 * It establishes communication flows based on the bit-reversed IDs of hosts.
 *
 * - The `N_` hosts are mapped to temporary IDs from `0` to `N_ - 1`,
 * - Each host initiates a flow to the host whose ID is the bit-reversal
 *   of its own ID, considering only the least significant `log2(N_)` bits.
 *
 * This pattern is commonly used to simulate non-local, structured traffic,
 * particularly in FFT-based algorithms and butterfly network topologies.
 *
 * Example:
 * For `N_ = 8` (3-bit addresses), host `0b010` (2) will send to `0b010` (2),
 * and host `0b011` (3) will send to `0b110` (6).
 */

@Serializable
@SerialName("bit-reversal")
internal data object BitReversal: SyntheticWl<Network> {
    context(NetSimScope)
    override suspend fun startSyntheticFlows(net: Network, demandMapping: (HostNode) -> DataRate) {
        val hosts = net.getNodesById<HostNode>()

        // Maps temporary ids 0 to nHosts to the corresponding hosts.
        // Temporary ids are used for bit reversal operation.
        val map: Map<NodeId, HostNode> = hosts.values.mapIndexed { idx, h ->
            NodeId(idx.toLong()) to h
        }.toMap()

        // Warn if the number of hosts is not a power of 2.
        var pwr2 = true
        if (map.size.isPow2().not()) {
            log.warn("starting bit-complement synthetic flows with number of hosts not a power of 2")
            pwr2 = false
        }

        // Number of bits to obe used for bit complement.
        val nBits = ceil(log2(map.size.toDouble())).toInt()

        // Masks the bits that will be inverted.
        val mask = (1L shl nBits) - 1

        map.forEach { (tmpId, h) ->
            var destIdInt = tmpId.value.revNBits(nBits)

            // If `N_` least significant bits are palindromes, then use complement instead.
            if (destIdInt == tmpId.value)
                destIdInt = destIdInt xor mask

            while (NodeId(destIdInt) !in map || destIdInt == tmpId.value) {
                assert(pwr2.not())
                // Step only needed if the number of hosts is not a power of 2.
                destIdInt =
                    if (destIdInt != 0L) destIdInt.clearLeftMostBit()
                    else map.keys.random(config.random).value
            }

            // The destination host.
            val destH = map[NodeId(destIdInt)]!!

            // The new flow to be started.
            val f = devConfig.netFlowConfig.version(
                senderId = h.id,
                destId = destH.id,
                demand = demandMapping(h),
            )

            net.startFlow(f)
        }
    }

    private fun Int.isPow2(): Boolean = this > 0 && (this and (this - 1)) == 0

    private fun Long.clearLeftMostBit(): Long =
        this - (floor(log2(this.toDouble())).toLong())

    private fun Long.revNBits(n: Int): Long {
        val nBitsMask = (1L shl n) - 1
        val mostSignMask = 1L shl (n - 1)
        val packman = this and mostSignMask != 0L

        return (this shl 1 and nBitsMask).let {
            if (packman) it or 1L
            else it
        }
    }
}
