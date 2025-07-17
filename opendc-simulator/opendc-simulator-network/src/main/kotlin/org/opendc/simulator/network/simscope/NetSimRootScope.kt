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

package org.opendc.simulator.network.simscope

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.opendc.common.annotations.ProtectedUse
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.integration.JNetController
import org.opendc.simulator.network.api.integration.NetSimGlobal
import org.opendc.simulator.network.api.integration.netBlking
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.networks.NetSpecs
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.custom.CustomNetwork
import org.opendc.simulator.network.export.NetSimExporter
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.simscope.fwpool.NetSimFWPool
import org.opendc.simulator.network.simscope.ip.NetSimAddressManager
import org.opendc.simulator.network.utils.NetCoId
import org.opendc.simulator.network.utils.SetOnce
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The root [NetSimScope] of a network simulation.
 */
internal class NetSimRootScope private constructor(
    private val wrappedScope: CoroutineScope,
) : NetSimScope, CoroutineScope by wrappedScope {
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetSimScope
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @OptIn(ProtectedUse::class)
    override val root: NetSimRootScope = this
    override val netCoId: NetCoId = coroutineContext[NetCoId]!!
    override val config: NetSimConfig = coroutineContext[NetSimConfig]!!
    override val tmSrc: NetSimTmSrc<*> = coroutineContext[NetSimTmSrc]!!
    override val barrier: NetSimBarrier = coroutineContext[NetSimBarrier]!!
    override val poolAggr: NetSimFWPool = coroutineContext[NetSimFWPool]!!
    override val idDispenser: NetSimIdDispenser = coroutineContext[NetSimIdDispenser]!!
    override val enRecorder: NetSimEnRecorder = coroutineContext[NetSimEnRecorder]!!
    override val addrMngr: NetSimAddressManager = coroutineContext[NetSimAddressManager]!!
    override val exporter: NetSimExporter? = coroutineContext[NetSimExporter]
    override val net: Network<*> get() = _net
    private lateinit var _net: Network<*>
    override var jNetController: JNetController by SetOnce()
    override val log by logger()

    override fun launch(
        ctx: CoroutineContext,
        block: suspend NetSimScope.() -> Unit,
    ): Job = super<NetSimScope>.launch(ctx, block)

    override suspend fun <T> coroutineScope(block: suspend NetSimScope.() -> T): T = super<NetSimScope>.coroutineScope(block)

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetSimRootScope Methods
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Checks that all configurations are compatible with each other.
     */
    private suspend fun checkRequirements() =
        with(this) {
            routPolicy.checkRequirements()
        }

    /**
     * Initializes fly-weight dispensers for this simulation env.
     */
    private suspend fun initDispensers() {
        devConfig.nodeConfig.version.initDispensers()
        devConfig.netFlowConfig.version.initDispensers()
    }

    override fun registerNetwork(net: Network<*>) {
        require(::_net.isInitialized.not()) {
            "A network was already registered for this ctx"
        }
        _net = net
    }

    override suspend fun sync(forceUpdt: Boolean) {
        barrier.awaitStability()
        barrier.whileStable(NetSimStabilityMode.ASSUMED) { // TODO: change
            enRecorder.sync(forceUpdt)
        }
        net.flowsById.values.forEach { it.sync(forceUpdt) }
        barrier.awaitStability()
        // TODO: net.sync
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Init
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    init {
        // Ensure that when [NetSimMainScope]'s [CoroutineScope.cancel] is called,
        // the [exporter], if present, is properly closed upon coroutine completion.
        coroutineContext[Job]!!.invokeOnCompletion {
            exporter?.close()
        }

        netBlking(this) {
            initDispensers()
            checkRequirements()
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    companion object {
        internal operator fun invoke(ctx: CoroutineContext = EmptyCoroutineContext): NetSimRootScope {
            // Use the network simulation configuration from [rootScope] if available; otherwise, fall back to a default configuration.
            val config = ctx[NetSimConfig] ?: NetSimConfig()
            // Use the provided [NetSimTmSrc.External] from [rootScope] if available; otherwise, use the default internal time source.
            // The time source determines synchronization and ensures simulation consistency.
            val tmSrc = ctx[NetSimTmSrc] ?: NetSimTmSrc.Internal()
            // If [rootScope] contains a Job, this creates a child Job of it; otherwise, creates a standalone Job.
            val mainJob = Job(ctx[Job])

            var newCtx =
                ctx + mainJob + config + tmSrc +
                    // Assigns a unique [NetCoId] to each coroutine launched within the [NetSimScope].
                    // This is used for concurrency assertions, logic validation, and identifying which independently executing
                    // network component the coroutine belongs to.
                    NetCoId.new(owner = NetCo.MAIN) +

                    // The coroutine name of the root env, so that it can be identified/retrieved/canceled.
                    CoroutineName(NetSimGlobal.NETSIMSCOPE_ROOT_CONAME) +

                    // Helps to ensure simulation consistency across concurrently running network components.
                    NetSimBarrier(config) +

                    // Dispenses unique IDs within this network simulation main env.
                    NetSimIdDispenser() +

                    // Provides flyweight instances of common simulation objects (e.g., [Evnt], [Msg], etc.),
                    // improving performance through object reuse.
                    NetSimFWPool() +

                    // Helps to track and record the total energy consumption of the simulated network.
                    NetSimEnRecorder(tmSrc) +

                    // Defines the routing and fairness policy used during the simulation.
                    config.routPolicy +

                    // Assigns unique IP addresses to nodes and manages hierarchical subnet structures
                    // to optimize routing and accurately represent network topology.
                    NetSimAddressManager()

            // If export configuration is defined, extend [newCtx] with a [NetSimExporter].
            // This component is responsible for exporting snapshots of the network, nodes, and flows to output files.
            config.exportConfig?.run { newCtx += NetSimExporter(config.exportConfig) }

            return NetSimRootScope(wrappedScope = CoroutineScope(newCtx))
        }

        internal operator fun invoke(
            ctx: CoroutineContext = EmptyCoroutineContext,
            spec: NetSimScopeSpec,
        ): NetSimRootScope {
            var rootCtx: CoroutineContext = ctx

            //
            // Assert the network file path exists.
            val netFile =
                spec.netPath?.let {
                    File(it)
                }
            netFile?.let {
                require(it.exists()) { it.absolutePath }
            }

            //
            // Build the network coroutine context from specs.
            rootCtx += spec.config
            spec.injectedTmSrc?.let { tmSrc ->
                rootCtx += tmSrc
            }
            spec.injectedInitialTs?.let { initialTs ->
                rootCtx += NetSimTmSrc.Internal(initialTs)
            }

            return NetSimRootScope(rootCtx).also { rootScope ->
                netBlking(rootScope) {
                    // If a path to a network topology defined then try to build it.
                    netFile?.let {
                        NetSpecs.fromFile(netFile).build()

                        // Else build an empty modifiable `CustomNetwork` in the rootScope.
                    } ?: CustomNetwork()

                    rootScope.checkRequirements()
                }
            }
        }
    }
}
