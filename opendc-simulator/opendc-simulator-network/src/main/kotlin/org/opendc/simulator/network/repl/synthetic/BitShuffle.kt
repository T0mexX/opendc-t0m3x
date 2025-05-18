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
 * Bit-shuffle synthetic traffic pattern.
 * It creates communication flows by cyclically rotating host ID bits to the left.
 *
 * - The `N_` hosts are assigned temporary IDs from `0` to `N_ - 1`,
 * - Each host sends to the host whose ID is a left rotation (bitwise shuffle)
 *   of its own ID by one position, considering only the least significant `log2(N_)` bits.
 *
 * This pattern is used to evaluate traffic dispersion in network topologies,
 * as it introduces structured, non-local communication similar to that
 * observed in FFT and butterfly computation stages.
 *
 * Example:
 * For `N_ = 8` (3-bit addresses), host `0b101` (5) will send to `0b011` (3),
 * since rotating `101` left by 1 gives `011`.
 */
@Serializable
@SerialName("bit-shuffle")
internal data object BitShuffle: SyntheticWl<Network> {
    context(NetSimScope)
    override suspend fun startSyntheticFlows(net: Network, demandMapping: (HostNode) -> DataRate) {
        val hosts = net.getNodesById<HostNode>()

        // Maps temporary ids 0 to nHosts to the corresponding hosts.
        // Temporary ids are used for bit complement operation.
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
            var destIdInt = when (tmpId.value) {
                // Reversal of `0` and `mask` are themselves, hence we make them send to each other.
                0L -> mask
                mask -> 0L
                else -> tmpId.value.rotateNBits(nBits)
            }

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

    private fun Long.rotateNBits(n: Int): Long {
        val mask = (1L shl n) - 1
        val lsb = this and mask
        val rotated = ((lsb shl 1) or (lsb ushr (n - 1))) and mask
        return (this and mask.inv()) or rotated
    }
}
