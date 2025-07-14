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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opendc.common.annotations.DebuggingUse
import org.opendc.common.annotations.ProtectedUse
import org.opendc.simulator.network.api.integration.JNetController
import org.opendc.simulator.network.api.integration.JNetFTracker
import org.opendc.simulator.network.components.NetCo
import org.opendc.simulator.network.components.evntemitter.Evnt
import org.opendc.simulator.network.components.invalidatable.Invalidatable
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.export.NetSimExporter
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.simscope.fwpool.FWPool
import org.opendc.simulator.network.simscope.fwpool.NetSimFWPool
import org.opendc.simulator.network.simscope.ip.NetSimAddressManager
import org.opendc.simulator.network.utils.NetCoId
import org.slf4j.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Represents the scope of a network simulation, encapsulating all resources,
 * configurations, and state needed for simulation execution.
 *
 * Structured coroutine concurrency is used for network simulation,
 * with the root network simulation coroutine scope being [NetSimRootScope].
 *
 * All network entities operate within this scope or its sub-scopes,
 * which ensures contextual consistency and resource access.
 *
 * @see NetSimRootScope
 */
internal interface NetSimScope : CoroutineScope {
    /**
     * Root scope for the entire simulation.
     * From any [NetSimScope], one can launch a coroutine as child to the root scope with [launchInRoot].
     */
    @ProtectedUse
    val root: NetSimRootScope

    /**
     * Contains all necessary configurations for initializing and controlling the simulation environemnt.
     */
    val config: NetSimConfig

    /**
     * Each [NetSimScope] has [NetCoId]. This is used for concurrency control, such as ownership validation on shared resources,
     * distributing access to shared resources such as [NetSimFWPool] (with multiple sub-pools), as well as detecting unmeant use of methods and such.
     *
     * Not every coroutine launched in a [NetSimScope] has a unique id; if not provided, the coroutine will inherit the parent one.
     * (This is intended behaviour—the coroutine id is more an identifier of the component that owns the coroutine, which might start
     * multiple coroutines for certain execution steps for performance purposes).
     *
     * Most runtime assertions that make use of this context element must be enabled with the VM option `-ea`.
     */
    val netCoId: NetCoId

    /**
     * The network simulation virtual time source. This can be either internally handled ([NetSimTmSrc.Internal]), or externally handled ([NetSimTmSrc.External]).
     * - network-workload-sim: *internally handled*
     * - network-synthetic-workload-sim: *internally handled*
     * - network-repl-sim: *internally handled*
     * - network-compute-sim: *externally handled*
     */
    val tmSrc: NetSimTmSrc<*>

    /**
     * Synchronization barrier for managing and enforcing *network stability*.
     *
     * The barrier ensures coordinated access to the network state by providing three modes of interaction:
     *
     * - **Stability awaiting**: Suspends until all [Invalidatable] components
     *   have been validated and the network is in a stable state.
     *
     * - **Stability checking**: Verifies that a block of code executes entirely during a stable period.
     *   If the network becomes unstable while the block is running, an error is thrown.
     *
     * - **Stability enforcing**: Ensures that a block of code runs without interference from other operations
     *   that could destabilize the network. Any changes that would make the network unstable are suspended
     *   until the block completes.
     *
     * This mechanism is critical for maintaining consistency across asynchronous components
     *
     */
    val barrier: NetSimBarrier

    /**
     * Aggregates all shared flyweight object pools ([FWPool]) used in the simulation.
     *
     * These pools manage reusable instances of frequently created objects such as [Msg], [Evnt], and others,
     * to avoid the overhead of repeated allocation and garbage collection.
     */
    val poolAggr: NetSimFWPool

    /**
     * Single simulation scoped distributor of unique ids.
     */
    val idDispenser: NetSimIdDispenser

    /**
     * Component that tracks network energy consumption
     */
    val enRecorder: NetSimEnRecorder

    /**
     * Handles export of snapshots during simulation.
     * `null` if [NetSimConfig.exportConfig] is `null`.
     */
    val exporter: NetSimExporter?

    /**
     * Assigns unique IP addresses to nodes and manages hierarchical subnet structures
     * to optimize routing and accurately represent network topology.
     */
    val addrMngr: NetSimAddressManager

    /**
     * The network instance associated with this simulation context.
     *
     * This property is automatically assigned when a network is initialized within this [NetSimScope]
     * or any of its child scopes.
     *
     * Only one network can be initialized and associated per [NetSimScope].
     */
    val net: Network<*>

    /**
     * Used only for externally controlled simulation controlled by a non-suspending context.
     * Used to queue up previously set handlers (e.g., in [JNetFTracker]) to be executed sequentially
     * with [JNetController.execCallbacks].
     */
    val jNetController: JNetController?

    /**
     * The logger associated with this network simulation scope.
     */
    val log: Logger

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Commodity Accessors.
    // // Getters used to avoid *Law of Demeter* on frequently used config properties.
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    val devConfig: NetSimDevConfig get() = config.netSimDevConfig

    val routPolicy: RoutPolicy get() = config.routPolicy

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Used to one-time register a network in this network simulation scope.
     * Invoked automatically when a [Network] is instantiated in any of the [NetSimRootScope] child scopes / coroutines.
     */
    @OptIn(ProtectedUse::class)
    fun registerNetwork(net: Network<*>) = root.registerNetwork(net)

    /**
     * The following is executed:
     * 1. Waiting for stability
     * 2. All metrics and states of the network are advanced so that they are synchronized with [tmSrc].
     * 3. Waiting for stability again to allow listeners to handle events
     * that may have been emitted after time was advanced.
     */
    suspend fun sync(forceUpdt: Boolean = false)

    /**
     * Replaces the extension function [CoroutineScope.launch] with similar behavior,
     * but the [CoroutineScope] receiver is wrapped as a [NetSimScope] instead of a plain [CoroutineScope].
     *
     * This allows the launched coroutine executing [block] to operate within a [NetSimScope],
     * providing access to network simulation context and utilities.
     *
     * @param ctx Optional coroutine context elements to add or override.
     * @param block The suspend function to execute, with a [NetSimScope] receiver.
     * @return The [Job] representing the launched coroutine.
     */
    @OptIn(ProtectedUse::class)
    fun launch(
        ctx: CoroutineContext = EmptyCoroutineContext,
        block: suspend NetSimScope.() -> Unit,
    ): Job {
        assert(this.isActive)
        // Cast to call extension function [CoroutineScope.launch] instead of this.
        return (this as CoroutineScope).launch(ctx) scopeToWrap@ {
            // Wrapp the newly created child [CoroutineScope] with [NetSimScope].
            NetSimSubScope(
                rootScope = root,
                wrappedScope = this@scopeToWrap,
            ).block()
        }
    }

    /**
     * Launches a new coroutine with a [NetSimScope] receiver, using the [NetSimRootScope] as its parent scope
     * instead of the current scope.
     *
     * This is useful when a coroutine must outlive the current scope
     * @see launch
     */
    @OptIn(ProtectedUse::class)
    fun launchInRoot(
        ctx: CoroutineContext = EmptyCoroutineContext,
        block: suspend NetSimScope.() -> Unit,
    ): Job {
        assert(root.isActive)
        return root.launch(ctx, block)
    }

    /**
     * TODO
     */
    @OptIn(ProtectedUse::class)
    fun <T> async(
        ctx: CoroutineContext = EmptyCoroutineContext,
        block: suspend NetSimScope.() -> T
    ): Deferred<T> =
        (this as CoroutineScope).async(ctx) scopeToWrap@ {
            NetSimSubScope(
                rootScope = root,
                wrappedScope = this@scopeToWrap,
            ).block()
        }

    /**
     * TODO
     */
    @OptIn(ProtectedUse::class)
    fun <T> asyncInRoot(
        ctx: CoroutineContext = EmptyCoroutineContext,
        block: suspend NetSimScope.() -> T
    ): Deferred<T> = root.async(ctx, block)


    /**
     * Replaces the standard [kotlinx.coroutines.coroutineScope] with a variant that wraps the receiver
     * [CoroutineScope] as a [NetSimScope].
     *
     * This enables the execution of the given [block] within a structured concurrency scope that
     * provides full access to the network simulation context and utilities.
     *
     * The resulting scope inherits the current coroutine context and starts a new child [Job],
     * ensuring that any launched coroutines are properly bound to the new structured scope.
     *
     * @param block The suspend function to execute, with a [NetSimScope] receiver.
     * @return The result of the [block] execution.
     */
    @OptIn(ProtectedUse::class)
    suspend fun <T> coroutineScope(block: suspend NetSimScope.() -> T): T =
        NetSimSubScope(
            rootScope = root,
            wrappedScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job])),
        ).block()

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // NetSimSubScope
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Any direct or indirect child of a [NetSimRootScope].
     */
    private class NetSimSubScope(
        rootScope: NetSimRootScope,
        private val wrappedScope: CoroutineScope,
    ) : NetSimScope by rootScope, CoroutineScope by wrappedScope {
        override val coroutineContext: CoroutineContext get() = wrappedScope.coroutineContext
        override val netCoId: NetCoId = coroutineContext[NetCoId]!!
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Debugging / Testing
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Produces a textual representation of the coroutine hierarchy rooted at this scope.
     *
     * This is primarily intended for debugging or testing purposes.
     *
     * @return A multi-line [String] showing the coroutine tree, with indentation representing parent-child relationships.
     */
    @DebuggingUse
    fun fmtCoTree(): String {
        fun inner(
            coCtx: CoroutineContext,
            strBuilder: StringBuilder,
            indent: String = "",
        ) {
            val name = coCtx[CoroutineName]?.name ?: "Unnamed"
            val job = coCtx[Job]!!
            strBuilder.appendLine(
                "$indent- Job(name=$name): $job, isActive=${job.isActive}, isCompleted=${job.isCompleted}, isCancelled=${job.isCancelled}",
            )
            job.children.forEach { child ->
                inner(child, strBuilder, "$indent  ")
            }
        }

        val strBuilder = StringBuilder()
        inner(coroutineContext, strBuilder)

        return strBuilder.toString()
    }
}
