package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.utils.W
import org.opendc.simulator.network.utils.ifNanThen
import java.time.Instant

public data class NetworkSnapshot internal constructor(
    public val instant: Instant,
    public val numOfNodes: Int,
    public val numOfHostNodes: Int,
    public val assignedHostNodes: Int,
    public val numOfCoreSwitches: Int,
    public val numOfActiveFlows: Int,
    public val totThroughputPerc: Double,
    public val avrgThroughputPerc: Double,
    public val totEnergyConsumpt: W
) {
    @OptIn(ExperimentalStdlibApi::class)
    public fun fmt(flags: Int = ALL): String {
        val colWidth = 25

        val firstLine = buildString {
            appendLine()
            flags.withFlagSet(INSTANT) { append("| " + "instant".padEnd(30)) }
            flags.withFlagSet(NODES) { append("nodes".padEnd(colWidth)) }
            flags.withFlagSet(HOST_NODES) { append("hosts (assigned)".padEnd(colWidth)) }
            flags.withFlagSet(CORE_SWITCHES) { append("core switches".padEnd(colWidth)) }
            flags.withFlagSet(FLOWS) { append("active flows".padEnd(colWidth)) }
            flags.withFlagSet(TOT_THROUGHPUT) { append("tot throughput %".padEnd(colWidth)) }
            flags.withFlagSet(AVRG_THROUGHPUT) { append("avrg throughput %".padEnd(colWidth)) }
            flags.withFlagSet(ENERGY) { append("energy consumed (KWh)".padEnd(colWidth)) }
            appendLine()
        }

        val secondLine = buildString {
            flags.withFlagSet(INSTANT) { append("| ${instant.toString().padEnd(30)}") }
            flags.withFlagSet(NODES) { append(numOfNodes.toString().padEnd(colWidth)) }
            flags.withFlagSet(HOST_NODES) { append("${numOfHostNodes}(${assignedHostNodes})".padEnd(colWidth)) }
            flags.withFlagSet(CORE_SWITCHES) { append(numOfCoreSwitches.toString().padEnd(colWidth)) }
            flags.withFlagSet(FLOWS) { append(numOfActiveFlows.toString().padEnd(colWidth)) }
            flags.withFlagSet(TOT_THROUGHPUT) { append(String.format("%.5f", totThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(AVRG_THROUGHPUT) { append(String.format("%.5f", avrgThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(ENERGY) { append(String.format("%.5f", totEnergyConsumpt).padEnd(colWidth)) }
        }

        return firstLine + secondLine
    }

    public companion object {
        public const val INSTANT: Int = 1
        public const val NODES: Int = 1 shl 1
        public const val HOST_NODES: Int = 1 shl 2
        public const val CORE_SWITCHES: Int = 1 shl 3
        public const val FLOWS: Int = 1 shl 4
        public const val TOT_THROUGHPUT: Int = 1 shl 5
        public const val ENERGY: Int = 1 shl 6
        public const val AVRG_THROUGHPUT: Int = 1 shl 7
        public const val ALL: Int = -1

        private inline fun Int.withFlagSet(flag: Int, block: () -> Unit) {
            if (this and flag != 0)
                block()
        }

        public fun NetworkController.snapshot(): NetworkSnapshot {
            val network = this.network

            runBlocking { network.awaitStability() }

//            if (network.flowsById.values.let { fs -> fs.sumOf { it.throughput } / fs.sumOf { it.demand } } < 1.0) {
//                network.flowsById.values.filterNot { it.demand == .0 }.forEach { print("[${it.demand} -> ${it.throughput}], ") }
//            }

            return NetworkSnapshot(
                instant = this.currentInstant,
                numOfNodes = network.nodes.size,
                numOfHostNodes = network.getNodesById<HostNode>().size,
                assignedHostNodes = this.claimedHostIds.size,
                numOfCoreSwitches = network.getNodesById<CoreSwitch>().size,
                numOfActiveFlows = network.flowsById.values.filterNot { it.demand == .0 }.size,
                totThroughputPerc = network.flowsById.values.let { fs -> fs.sumOf { it.throughput } / fs.sumOf { it.demand } },
                avrgThroughputPerc = network.flowsById.values.filterNot { it.demand == .0 }.let { fs -> fs.sumOf { (it.throughput / it.demand) ifNanThen .0 } / fs.size },
                totEnergyConsumpt = this.energyRecorder.totalConsumption
            )
        }
    }
}
