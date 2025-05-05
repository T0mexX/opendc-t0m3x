package org.opendc.simulator.network.repl.cmds.flow

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.common.units.Unit
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.repl.synthetic.SyntheticWl

private const val CMD_STR: String = "synthetic-wl"

/**
 * TODO
 */
internal class FlowSynthWlCmd: REPLCmd(name = CMD_STR) {
    private val synthWl: SyntheticWl<Network> by argument(
        help = "",
    ).convert { str ->
        // Wrap into JSON object to use built in polymorphic deserialization.
        Json.decodeFromString("""{ "type": "$str" } """)
    }

    private val demand: Unit<*> by argument(
        help = "The demand of the synthetic flows (e.g. '1 Gbps' or '100%'). " +
            "If a percentage is used, that percentage of the tx bandwidth of " +
            "the sender host is used as demand",
    ).convert { str ->
        kotlin.runCatching {
            return@convert Json.decodeFromString<DataRate>(str)
        }

        kotlin.runCatching {
            return@convert Json.decodeFromString<Percentage>(str)
        }

        fail("either a data-rate (e.g. '1Gbps') or a load percentage (e.g. '100%') should be passed as parameter")
    }

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "synth" to listOf(CMD_STR),
            "synth-wl" to listOf(CMD_STR),
        )

    override fun run() = execREPLCmdCatching {
        (demand as? DataRate)?.let { dr ->
            synthWl.startSyntheticFlows(net) { dr }
        }

        (demand as? Percentage)?.let { perc ->
            synthWl.startSyntheticFlows(net) { h ->
                h.portSpeed * h.nPorts * perc
            }
        }
    }
}
