/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.playground.cmds

import kotlinx.coroutines.CoroutineScope
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.cmds.NewLink.regex

/**
 * Creates a new (core)switch with certain specs.
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 *
 * // switch
 * > switch 0 /* node id */ - 1Gbps /* port speed */ 4 /* num of ports */
 * 6:42:29.719 [INFO] NEW_SWITCH - successfully added [Switch: id=10] to network
 *
 * // core switch (connected to internet)
 * > core switch 0 /* node id */ - 1Gbps /* port speed */ 4 /* num of ports */
 */
internal data object NewSwitch : PGCmd("NEW_SWITCH") {
    override val regex = Regex("\\s*(c|core|)\\s*(?:s|switch)\\s+(\\d+)\\s+([^ ]+)\\s+([^ ]+)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val customNetwork: CustomNetwork =
            (coroutineContext[PGEnv]!!.network as? CustomNetwork)
                ?: return cancelAfter { log.error("adding a switch is not allowed unless the network is custom type") }

        val nodeId: NodeId = fromStrElseCanc(result.groupValues[2])
        val portSpeed: DataRate = fromStrElseCanc(result.groupValues[3])
        val numOfPorts: Int = fromStrElseCanc(result.groupValues[4])

        val newSwitch: Switch =
            let {
                if (result.groupValues[1].isEmpty()) {
                    // If not a Core Switch.
                    Switch(
                        id = nodeId,
                        portSpeed = portSpeed,
                        numOfPorts = numOfPorts,
                    )
                } else {
                    // If a Core Switch
                    CoreSwitch(
                        id = nodeId,
                        portSpeed = portSpeed,
                        numOfPorts = numOfPorts,
                    )
                }
            }

        customNetwork.addNode(newSwitch)
            ?.let { log.info("successfully added $newSwitch to network") }
            ?: log.error("unable to add switch.")
    }
}
