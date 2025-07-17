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

package org.opendc.simulator.network.components

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.opendc.common.annotations.ProtectedUse
import org.opendc.simulator.network.components.evntemitter.Evnt
import org.opendc.simulator.network.components.evntemitter.EvntEmitter
import org.opendc.simulator.network.components.invalidatable.Invalidatable
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.msgable.Msgable
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.utils.NetCoId
import org.opendc.simulator.network.utils.SetOnce
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A component that runs its own coroutine-based logic as part of a network simulation.
 *
 * Implementing this interface allows the component to execute concurrently with others,
 * using a dedicated coroutine automatically launched via [netRun] during initialization.
 *
 * The component's coroutine:
 * - Is started by [netRun], typically called when the simulation initializes the component.
 * - Can be canceled independently via [netCancel].
 * - Is automatically canceled if the associated [NetSimCtxOld] is closed or canceled.
 *
 * This mechanism enables fine-grained parallelism of network components,
 * coordinated through messages ([Msg]), events ([Evnt]),
 * and synchronization via [NetSimBarrier] and [Invalidatable] logic.
 *
 * Every [NetRunnable] is an [Invalidatable] but not every [Invalidatable] is a [NetRunnable] (e.g., some [Evnt]s).
 *
 * @see NetSimBarrier for network consistency and stability.
 * @see Invalidatable for stability invalidation of a single component.
 * @see Msg for notifying a component (if [Msgable]).
 * @see Evnt for handling events emitted by components (if [EvntEmitter]).
 */
internal interface NetRunnable {
    /**
     * The coroutine [Job] representing this component’s running logic.
     * It is launched by [netRun] and controls the coroutine lifecycle.
     */
    @ProtectedUse
    var job: Job

    /**
     * The main function of this [NetRunnable]. Likely to run until [netCancel] is invoked.
     */
    context(NetSimScope)
    @ProtectedUse
    suspend fun netRunnableMain()

    context(NetSimScope)
    @ProtectedUse
    suspend fun netRunnableCancellationCleanup()

    /**
     * TODO: change
     * Starts the component’s coroutine logic as a child of the current [NetSimCtxOld] job.
     *
     * @param additionalCtx Additional coroutine context elements to add,
     * it must at least include a [NetCoId] and a [CoroutineName].
     */
    context(NetSimScope)
    @ProtectedUse
    fun netRun(additionalCtx: CoroutineContext = EmptyCoroutineContext) {
        log.debug { "launching $this `NetRunnable` in network simulation scope" }
        job = launchInRoot(additionalCtx) runnableScope@ {
            try {
                // Run the main [NetRunnable] function.
                netRunnableMain()
            } finally {
                // On cancellation/completion run a suspending cleanup in the same [NetSimScope].
                // ([job.invokeOnCompletion] is not a suspend callback).
                withContext(NonCancellable) {
                    netRunnableCancellationCleanup()
                }
            }
        }
    }

    /**
     * Cancels the coroutine running this component’s logic.
     * This can be used to stop the component independently of the enclosing context.
     */
    context(NetSimScope)
    @OptIn(ProtectedUse::class)
    fun netCancel() {
        log.debug { "canceling $this `NetRunnable` in network simulation scope" }
        job.cancel()
    }
}
