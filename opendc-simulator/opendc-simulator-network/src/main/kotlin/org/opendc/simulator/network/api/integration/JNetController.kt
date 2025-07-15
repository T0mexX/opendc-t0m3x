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

package org.opendc.simulator.network.api.integration

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.coroutines.channels.Channel
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.export.NetworkExportConfig
import org.slf4j.Logger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

public class JNetController internal constructor(
    private val netController: NetController,
) : AutoCloseable by netController, AbstractCoroutineContextElement(Key), Logger by netController {
    init {
        // Add this adapter in the network simulation scope so that
        // callbacks can be queued to [callbacksChl] and executed
        // sequentially from a non-suspending context.
        netController.rootScope.jNetController = this
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // JAdapter Logic
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    internal val callbacksChl = Channel<() -> Unit>(capacity = Channel.UNLIMITED)

    /**
     * @return The number of callbacks executed.
     */
    public fun execCallbacks(): Int {
        var count = 0
        var f = callbacksChl.tryReceive().getOrNull()
        while (f != null) {
            f()
            count++
            f = callbacksChl.tryReceive().getOrNull()
        }

        return count
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Terminal Interface Claiming Logic
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public fun claimTerminal(ip: IPv4Address): JNetIFace = JNetIFace(netController.rootScope, netController.claimTerminal(ip))

    public fun claimTerminal(id: NodeId): JNetIFace = JNetIFace(netController.rootScope, netController.claimTerminal(id))

    public fun claimTerminal(): JNetIFace = JNetIFace(netController.rootScope, netController.claimTerminal())

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Synchronization of Simulation Time
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @JvmOverloads
    public fun sync(forceUpdt: Boolean = false): Unit = netController.syncBlocking(forceUpdt)

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Export
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Immediately exports all network and node information based on the current simulation state.
     *
     * The exported data includes all fields specified in [NetworkExportConfig.nodeExportColumns]
     * and [NetworkExportConfig.networkExportColumns], and is written to the directory defined
     * by [NetworkExportConfig.outputFolder].
     *
     * @throws IllegalStateException If the current simulation time is not
     * aligned with an expected export timestamp as defined in [NetworkExportConfig].
     */
    public fun exportNow(): Unit = netController.exportNowBlocking()

    internal companion object Key : CoroutineContext.Key<JNetController>
}
