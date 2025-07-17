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
import kotlinx.coroutines.cancel
import org.opendc.common.annotations.DebuggingUse
import org.opendc.simulator.network.api.NetIFace
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.NodeId.Companion.toNId
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.simscope.NetSimRootScope
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScopeSpec
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.SuspendingODCNApi
import org.slf4j.Logger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

public class NetController internal constructor(
    internal val rootScope: NetSimRootScope,
) : AutoCloseable, Logger by rootScope.log, AbstractCoroutineContextElement(Key) {
    public val jNetController: JNetController by lazy { JNetController(this) }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Terminal Interface Claiming Logic
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val claimedTerminals = mutableSetOf<Terminal>()

    public fun claimTerminal(ip: IPv4Address): NetIFace = claimTerminal(ip.toNId())

    public fun claimTerminal(id: NodeId): NetIFace =
        with(rootScope) {
            val t = net[id] as? Terminal
            t ?: throw IllegalStateException("Terminal with ip ${id.toIp()} not found")
            if (t in claimedTerminals) error("Terminal with ip ${id.toIp()} already claimed")

            claimedTerminals += t
            return NetIFace(t)
        }

    public fun claimTerminal(): NetIFace =
        with(rootScope) {
            val terms = net.getNodesById<Terminal>().values
            val t =
                terms.find {
                    it !in claimedTerminals
                } ?: throw IllegalStateException("Not enough available terminals (${terms.size})")

            claimedTerminals += t
            val bo = NetIFace(t)
            return bo
        }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Synchronization of Simulation Time
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * TODO
     */
    @JvmSynthetic
    @SuspendingODCNApi
    public suspend fun sync(forceUpdt: Boolean = false): Unit = rootScope.launch {
        sync(forceUpdt)
    }.join()

    public fun syncBlocking(forceUpdt: Boolean = false): Unit = netBlking(rootScope) {
        rootScope.sync(forceUpdt)
    }

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
    @JvmSynthetic
    @SuspendingODCNApi
    public suspend fun exportNow(): Unit = rootScope.launch {
            exportNowPrivate()
    }.join()

    public fun exportNowBlocking(): Unit = netBlking(rootScope) {
        exportNowPrivate()
    }

    context(NetSimScope)
    private suspend fun exportNowPrivate() {
        barrier.awaitStability()
        exporter?.exportNow(NetSimStabilityMode.ENFORCED)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Formatted.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * TODO
     */
    public fun fmtExportConfig(): String = rootScope.config.exportConfig?.fmt() ?: "==== No Network Export Config ===="

    /**
     * TODO
     */
    @SuspendingODCNApi
    public suspend fun fmtNet(stabilityMode: NetSimStabilityMode = NetSimStabilityMode.ASSUMED): String =
        rootScope.async {
            net.fmt(stabilityMode)
        }.await()

    /**
     * TODO
     */
    @SuspendingODCNApi
    public suspend fun fmtFlows(
        stabilityMode: NetSimStabilityMode = NetSimStabilityMode.ASSUMED,
        ls: Boolean = false
    ): String = rootScope.async {
        net.fmtFlows(stabilityMode, ls)
    }.await()

    @DebuggingUse
    public fun fmtCoTree(): String = rootScope.fmtCoTree()

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // AutoClosable
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @OptIn(DebuggingUse::class)
    override fun close() {
//        println("=== before closing(already canceled=${rootScope.isActive.not()}):\n" + rootScope.fmtCoTree())
//        sleep(1000)
        rootScope.cancel()
//        sleep(1000)
//        println("=== after closing:\n" + rootScope.fmtCoTree())
    }

    public companion object Key : CoroutineContext.Key<NetController> {
        public operator fun invoke(
            ctx: CoroutineContext,
            netScopeSpec: NetSimScopeSpec,
        ): NetController = NetController(NetSimRootScope(ctx = ctx, spec = netScopeSpec))

//        /**
//         * TODO
//         * Needed because fucking export coroutine runs while things
//         * are being updated in the fucking compute moduel, including the fucking simulation time.
//         * If the export takes too long then everything is fucked.
//         * TODO
//         */
//        @JvmSynthetic
//        public suspend fun <T> NetController?.whileNetTmSrcFrozen(assertFrozenAt: Timestamp? = null, block: suspend () -> T): T =
//            this?.let {
//                with(rootScope) {
//                    tmSrc.whileFrozen {
//                        block()
//                    }
//                }
//            } ?: block()
    }
}
