package org.opendc.simulator.network.repl.cmds.network

import org.opendc.simulator.network.repl.cmds.REPLCmd
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode

private const val CMD_STR: String = "prefix-trie"

internal class NetPrefixTrieCmd : REPLCmd(name = CMD_STR) {
    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "trie" to listOf(CMD_STR),
            "prefixes" to listOf(CMD_STR),
        )

    override fun run() = execREPLCmdCatching {
        barrier.whileStable(NetSimStabilityMode.ENFORCED) {
           echo(addrMngr.fmt())
        }
    }
}
