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

package org.opendc.simulator.network.repl.cmds.flow

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import kotlinx.serialization.json.Json
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.common.units.Unit
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.repl.synthetictraffic.SyntheticTraffic
import org.opendc.common.withProgressBarSus
import kotlin.time.measureTime

private const val CMD_STR: String = "synthetic-wl"

/**
 * TODO
 */
internal class FlowSynthWlCmd : REPLCmd(name = CMD_STR) {
    private val synthWl: SyntheticTraffic<Network<*>> by argument(
        help = "",
    ).convert { str ->
        // Wrap into JSON object to use built in polymorphic deserialization.
        SyntheticTraffic.fromString("""{ "type": "$str" } """)
    }

    private val demand: Unit<*> by argument(
        help =
            "The demand of the synthetic flows (e.g. '1 Gbps' or '100%'). " +
                "If a percentage is used, that percentage of the tx bandwidth of " +
                "the sender host is used as demand",
    ).convert { str ->
        kotlin.runCatching {
            return@convert Json.decodeFromString<DataRate>(str)
        }

        kotlin.runCatching {
            return@convert Json.decodeFromString<Percentage>(str)
        }

        fail("either a data-rate (e.g., '1Gbps') or a load percentage (e.g., '100%') should be passed as parameter")
    }.default(Percentage.ofPercentage(100))

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "synth" to listOf(CMD_STR),
            "synth-wl" to listOf(CMD_STR),
        )

    override fun run() =
        execREPLCmdCatching {
            val tm =
                measureTime {
                    withProgressBarSus("Executing Synthetic WL...") {
                        (demand as? DataRate)?.let { dr ->
                            synthWl.startSyntheticFlows(net) { dr }
                        }

                        (demand as? Percentage)?.let { perc ->
                            synthWl.startSyntheticFlows(net) { h ->
                                h.portSpeed * h.links.count { it != null } * perc
                            }
                        }
                    }

                    barrier.awaitStability()
                }

            echo("| Synthetic workload executed successfully in $tm")
        }
}
