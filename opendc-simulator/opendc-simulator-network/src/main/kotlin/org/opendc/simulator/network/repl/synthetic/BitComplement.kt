package org.opendc.simulator.network.repl.synthetic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.apache.hadoop.net.TableMapping
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
 * Bit-complement synthetic traffic pattern.
 * It allows starting flows according to bit-complement of host ids.
 *
 * - The `N_` hosts are mapped to temporary ids 0 to N_-1,
 * - Each host will start a flow directed to the bit-complement of its own id,
 *   considering only the least significant `log(N_)` bits.
 *
 * Example:
 * For `N_ = 8` (3-bit addresses), host `0b010` (2) will send to `0b101` (5).
 */
@Serializable
@SerialName("bit-complement")
internal data object BitComplement: SyntheticWl<Network> {
    context(NetSimScope)
    override suspend fun startSyntheticFlows(
        net: Network,
        pb: ProgressBar?,
        demandMapping: (HostNode) -> DataRate
    ) {
        val hosts = net.getNodesById<HostNode>()
        pb?.maxHint(hosts.size.toLong())

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
            var destIdInt = tmpId.value xor mask

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
            pb?.step()
        }
    }

    private fun Int.isPow2(): Boolean = this > 0 && (this and (this - 1)) == 0

    private fun Long.clearLeftMostBit(): Long =
        this - (floor(log2(this.toDouble())).toLong())
}
