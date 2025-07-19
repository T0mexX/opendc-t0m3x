package org.opendc.simulator

import kotlinx.coroutines.runBlocking
import org.opendc.common.SusRunnable
import org.opendc.common.withProgressBarSus

/**
 * TODO maintaining different level sof compatibility with non suspening code
 */
public class SusAwareSimulatorDispatcher: SimulationDispatcher() {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Suspending Adaptations for non suspending interface.
    /////// - The least number of bridge calls from non suspending context to suspending one the better performance.
    //////  - For even better performance, use the Suspending Versions directly from a suspending context.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun advanceUntilIdle(): Unit = runBlocking {
        advanceUntilIdleSus()
    }

    @Deprecated(
        message = "bad performance. Use `advanceBySus` already from a suspending context if possible",
        replaceWith = ReplaceWith("advanceUntilIdleSus()")
    )
    override fun advanceBy(deltaMs: Long): Unit = runBlocking {
        advanceBySus(deltaMs)
    }

    @Deprecated(
        message = "bad performance. Use `runCurrentSus` already from a suspending context if possible",
        replaceWith = ReplaceWith("runCurrentSus()")
    )
    override fun runCurrent(): Unit = runBlocking {
        runCurrentSus()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Suspending Versions.
    ////// - Versions invoked already from suspending context.
    ////// - The suspending scope these methods are invoked from should not have this as the dispatcher.
    //////   A new scope with a default dispatcher should be used, since this dispatcher is a custom dispatcher
    //////   whose correctness relies on the fact that coroutines launched with it never suspend i not
    //////   for scheduling a future continuation.
    ////// - [AdvanceUntilIdle] can be invoked form a scope with this custom dispatcher, and it is the easiest option
    //////   to achieve performance and not break the custom dispatcher that this class wraps.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public suspend fun advanceUntilIdleSus(): Unit = withProgressBarSus("Simulating...(min)") pb@ {
        while (true) {
            val deadline: Long = queue.peekDeadline()
            val task: Runnable? = queue.poll()

            // If no more tasks than break out the loop.
            task ?: break

            // Determine if a step will be taken in the progress bar. A step is take if
            currentTime = deadline

            // If the task is a suspending task ([SusRunnable]), then invoke
            // the suspending version directly to avoid multiple
            // bridging from non-suspending context and suspending one.
            (task as? SusRunnable)?.susRun()
                ?: task.run()

            stepTo(currentTime / 1000 / 60) // 1 step == 1min simulated
        }
    }

    public suspend fun advanceBySus(delayMs: Long) {
        require(delayMs >= 0) { "Can not advance time by a negative delay: $delayMs ms" }

        var target = currentTime + delayMs
        if (target < 0) {
            target = Long.MAX_VALUE
        }

        var deadline: Long

        while ((queue.peekDeadline().also { deadline = it }) < target) {
            val task = queue.poll() // Cannot be null since while condition is always false on an empty queue

            // If the task is a suspending task ([SusRunnable]), then invoke
            // the suspending version directly to avoid multiple
            // bridging from non-suspending context and suspending one.
            (task as? SusRunnable)?.susRun()
                ?: task.run()

            currentTime = deadline
        }

        currentTime = target
    }

    public suspend fun runCurrentSus() {

        while (queue.peekDeadline() == currentTime) {
            val task = queue.poll() ?: break

            // If the task is a suspending task ([SusRunnable]), then invoke
            // the suspending version directly to avoid multiple
            // bridging from non-suspending context and suspending one.
            (task as? SusRunnable)?.susRun()
                ?: task.run()
        }
    }
}
