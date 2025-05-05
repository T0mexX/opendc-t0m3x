package org.opendc.simulator.network.repl.cmds.flow

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.repl.cmds.REPLCmd

private const val CMD_STR = "updt"

internal class FlowUpdtCmd : REPLCmd(CMD_STR) {
    private val id: Long by argument(
        help = "The id of the flow whose demand is to be updated",
    ).long().check("flow does not exist") { long -> net.flowsById.contains(FlowId(long)) }

    private val newDemand: DataRate by option(
        help = "new demand",
        names = arrayOf("-b", "--bw", "--bandwidth")
    ).convert {
        decodeOrNull<DataRate>(it)
            ?: fail("Unable to parse data rate '$it' (e.g. 1Gbps)")
    }.required()

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "u" to listOf(CMD_STR),
            "update" to listOf(CMD_STR),
        )

    override fun run(): Unit = execREPLCmdCatching {
        barrier.awaitStability()
        val f = net.flowsById[FlowId(id)] ?: let {
            echo("invalid flow id", err = true)
            return@execREPLCmdCatching
        }
        f.setDemand(newDemand)
        barrier.awaitStability()
        echo("| Demand updated successfully, new throughput=${f.throughput}") ?: issueMessage("Unable to stop flow")
    }
}
