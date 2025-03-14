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

package org.opendc.simulator.network.repl.cmds

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.arguments.convert
import org.opendc.common.units.TimeDelta
import org.opendc.simulator.network.simscope.NetSimTmSrc
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode

private const val CMD_STR: String = "advance-time"

internal class AdvTimeCmd : REPLCmd(name = CMD_STR) {
    private val tmDelta: TimeDelta by argument(
        help = "The time period to advance virtual time by (E_.g. '5min')",
    ).convert {
        decodeOrNull<TimeDelta>(it)
            ?: fail("Unable to parse time parameter '$it' (E_.g. 5min)")
    }.check("time must be positive") { it > TimeDelta.zero }

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "adv" to listOf(CMD_STR),
            "adv-tm" to listOf(CMD_STR),
        ) + super.aliases()

    override fun run(): Unit =
        execREPLCmdCatching {
            barrier.whileStable(NetSimStabilityMode.ENFORCED) {
                (tmSrc as NetSimTmSrc.Internal).advanceBy(tmDelta)
                sync()
            }
            echo("| Advanced time by $tmDelta. Time elapsed since start: ${tmSrc.sinceStart}")
        }
}
