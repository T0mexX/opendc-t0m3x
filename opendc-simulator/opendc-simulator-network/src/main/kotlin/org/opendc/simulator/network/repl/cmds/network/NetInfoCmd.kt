package org.opendc.simulator.network.repl.cmds.network

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode


private const val CMD_STR: String = "info"

internal class NetInfoCmd : REPLCmd(name = CMD_STR) {
    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "i" to listOf(CMD_STR),
        )

    override fun run() = execREPLCmdCatching {
        barrier.whileStable(NetSimStabilityMode.ENFORCED) {
            sync(forceUpdt = true)
            echo(net.fmt())
        }
    }
}

