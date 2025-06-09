package org.opendc.simulator.network.repl.cmds.flow

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.common.units.Unit
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.networks.NetworkSpecs
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.repl.synthetic.SyntheticWl
import org.opendc.simulator.network.utils.NETWORK_SERIALIZERS_MODULE
import org.opendc.simulator.network.utils.withProgressBar
import java.lang.System.exit
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime

private const val CMD_STR: String = "synthetic-wl"

/**
 * TODO
 */
internal class FlowSynthWlCmd: REPLCmd(name = CMD_STR) {
    private val synthWl: SyntheticWl<Network> by argument(
        help = "",
    ).convert { str ->
        // Wrap into JSON object to use built in polymorphic deserialization.
        Json {
            serializersModule = NETWORK_SERIALIZERS_MODULE
        }.decodeFromString("""{ "type": "$str" } """)
    }

    private val demand: Unit<*> by argument(
        help = "The demand of the synthetic flows (E_.g. '1 Gbps' or '100%'). " +
            "If a percentage is used, that percentage of the tx bandwidth of " +
            "the sender host is used as demand",
    ).convert { str ->
        kotlin.runCatching {
            return@convert Json.decodeFromString<DataRate>(str)
        }

        kotlin.runCatching {
            return@convert Json.decodeFromString<Percentage>(str)
        }

        fail("either a data-rate (E_.g. '1Gbps') or a load percentage (E_.g. '100%') should be passed as parameter")
    }

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "synth" to listOf(CMD_STR),
            "synth-wl" to listOf(CMD_STR),
        )

    override fun run() = execREPLCmdCatching {
//        val pb = ProgressBarBuilder()
//            // May be changed by the specific wl.
//            .setStyle(ProgressBarStyle.ASCII)
//            .setTaskName("Executing Synthetic Workload...")
//            .build()
        val tm = measureTime {
            withProgressBar("Executing Synthetic WL...") {
                (demand as? DataRate)?.let { dr ->
                    synthWl.startSyntheticFlows(net) { dr }
                }

                (demand as? Percentage)?.let { perc ->
                    synthWl.startSyntheticFlows(net) { h ->
                        h.portSpeed * h.ports.count { it.txLink != null } * perc
                    }
                }

                barrier.awaitStability()
            }
        }

        echo("Synthetic workload executed successfully in $tm")
    }
}
