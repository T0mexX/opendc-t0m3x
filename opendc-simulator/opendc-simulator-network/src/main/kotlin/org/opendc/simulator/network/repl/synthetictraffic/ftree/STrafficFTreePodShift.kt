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

package org.opendc.simulator.network.repl.synthetictraffic.ftree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tongfei.progressbar.ProgressBar
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.ftree.FTree
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.repl.synthetictraffic.SyntheticTraffic
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.increaseMax

@Serializable
@SerialName("ftree-podshift")
internal object STrafficFTreePodShift : SyntheticTraffic<FTree>() {
    context(NetSimScope, ProgressBar)
    override suspend fun startSyntheticFlows(
        net: Network<*>,
        demandMapping: (Terminal) -> DataRate,
    ) {
        require(net is FTree)

        // Increase the number of actions to be taken to complete the current context
        // progress bar by the number of flows that will need to be started.
        this@ProgressBar.increaseMax(net.specs.N_.toLong())

        net.pods.forEachIndexed { pIdx, pod ->
            pod.hosts.forEachIndexed { hIdx, h ->
                // Destination pod.
                val destP = net.pods.getModuloIdx(pIdx + 1)
                // Destination host.
                val destH = destP.hosts[hIdx]

                net.startFlow(
                    devConfig.netFlowConfig.version(
                        srcId = h.id,
                        destId = destH.id,
                        dmnd = demandMapping(h),
                    ),
                )

                this@ProgressBar.step()
            }
        }
    }

    private fun <T> List<T>.getModuloIdx(idx: Int): T = this[idx % this.size]
}
