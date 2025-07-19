/*
 * Copyright (c) 2022 AtLarge Research
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

package org.opendc.simulator;

import java.time.Instant;
import java.time.InstantSource;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.opendc.common.Dispatcher;
import org.opendc.common.DispatcherHandle;
import org.opendc.common.ProgressBarUtilsKt;
import org.opendc.common.units.TimeDelta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Dispatcher} used by simulations to manage execution of (future) tasks, providing a controllable (virtual)
 * clock to skip over delays.
 *
 * <p>
 * The dispatcher can be queried to advance the time (via {@link #advanceBy}), run all the scheduled tasks advancing the
 * virtual time as needed (via {@link #advanceUntilIdle}), or run the tasks that are scheduled to run as soon as
 * possible but have not yet been dispatched (via {@link #runCurrent}). These methods execute the pending tasks using
 * a single thread.
 *
 * <p>
 * This class is not thread-safe and must not be used concurrently by multiple threads.
 */
public class SimulationDispatcher implements Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(SimulationDispatcher.class);
    /**
     * The {@link TaskQueue} containing the pending tasks.
     */
    protected final TaskQueue queue = new TaskQueue();

    /**
     * The current time of the scheduler in milliseconds since epoch.
     */
    protected long currentTime;

    /**
     * A counter to establish total order on the events that happen at the same virtual time.
     */
    protected int count = 0;

    /**
     * The {@link InstantSource} instance linked to this scheduler.
     */
    protected final SimulationClock timeSource = new SimulationClock(this);

    /**
     * Construct a {@link SimulationDispatcher} instance with the specified initial time.
     *
     * @param initialTimeMs The initial virtual time of the scheduler in milliseconds since epoch.
     */
    public SimulationDispatcher(long initialTimeMs) {
        this.currentTime = initialTimeMs;
    }

    /**
     * Construct a {@link SimulationDispatcher} instance with the initial time set to UNIX Epoch 0.
     */
    public SimulationDispatcher() {
        this(0);
    }

    /**
     * Return the current virtual timestamp of the dispatcher (in milliseconds since epoch).
     *
     * @return A long value representing the virtual timestamp of the dispatcher in milliseconds since epoch.
     */
    public long getCurrentTime() {
        return currentTime;
    }

    /**
     * Return the virtual time source associated with this dispatcher.
     *
     * @return A {@link InstantSource} tracking the virtual time of the dispatcher.
     */
    @Override
    public final InstantSource getTimeSource() {
        return timeSource;
    }

    @Override
    public final void schedule(long delayMs, Runnable command) {
        internalSchedule(delayMs, command);
    }

    @Override
    public final DispatcherHandle scheduleCancellable(long delayMs, Runnable command) {
        long target = currentTime + delayMs;
        if (target < 0) {
            target = Long.MAX_VALUE;
        }

        long deadline = target;
        int id = internalSchedule(delayMs, command);
        return () -> internalCancel(deadline, id);
    }

    /**
     * Run the enqueued tasks in the specified order, advancing the virtual time as needed until there are no more
     * tasks in the queue of this scheduler.
     */
    public void advanceUntilIdle() {
        ProgressBarUtilsKt.withProgressBar("Simulating...", (ProgressBar pb) -> {
            long logInterval = 1000 * 60 * 60 * 24; // day
            long nextLog = currentTime + logInterval;
            while (true) {
                long deadline = queue.peekDeadline();
                Runnable task = queue.poll();

                if (task == null) {
                    break;
                }

                boolean step = currentTime != deadline;
                currentTime = deadline;
                while (currentTime >= nextLog) {
                    log.debug("Days simulated: {}", nextLog / 1000 / 60 / 60 / 24); // days
                    nextLog = nextLog + logInterval;
                }
                task.run();
                if (step) pb.step();
            }
        });
    }

    /**
     * Move the virtual clock of this dispatcher forward by the specified amount, running the scheduled tasks in the
     * meantime.
     *
     * @param delayMs The amount of time to move the virtual clock forward (in milliseconds).
     * @throws IllegalStateException if passed a negative <code>delay</code>.
     */
    public void advanceBy(long delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("Can not advance time by a negative delay: " + delayMs + " ms");
        }

        long target = currentTime + delayMs;
        if (target < 0) {
            target = Long.MAX_VALUE;
        }

        final TaskQueue queue = this.queue;
        long deadline;

        while ((deadline = queue.peekDeadline()) < target) {
            Runnable task = queue.poll(); // Cannot be null since while condition is always false on an empty queue

            task.run();
            currentTime = deadline;
        }

        currentTime = target;
    }

    /**
     * Execute the tasks that are scheduled to execute at this moment of virtual time.
     */
    public void runCurrent() {
        final TaskQueue queue = this.queue;
        long currentTime = this.currentTime;

        while (queue.peekDeadline() == currentTime) {
            Runnable task = queue.poll();

            if (task == null) {
                break;
            }

            task.run();
        }
    }

    /**
     * Schedule a <code>task</code> that executes after the specified <code>delayMs</code>.
     *
     * @param delayMs The time from now until the execution of the task (in milliseconds).
     * @param task The task to execute after the delay.
     * @return The identifier of the task that can be used together with the timestamp of the task to cancel it.
     */
    private int internalSchedule(long delayMs, Runnable task) {
        if (delayMs < 0) {
            throw new IllegalArgumentException(
                    "Attempted scheduling an event earlier in time (delay " + delayMs + " ms)");
        }

        long target = currentTime + delayMs;
        if (target < 0) {
            target = Long.MAX_VALUE;
        }

        int id = count++;
        queue.add(target, id, task);
        return id;
    }

    /**
     * Cancel a pending task.
     *
     * @param deadline The deadline of the task.
     * @param id The identifier of the task (returned by {@link #internalSchedule(long, Runnable)}).
     * @return A boolean indicating whether a task was actually cancelled.
     */
    private boolean internalCancel(long deadline, int id) {
        return queue.remove(deadline, id);
    }

    /**
     * A {@link InstantSource} implementation for a {@link SimulationDispatcher}.
     */
    private static class SimulationClock implements InstantSource {
        private final SimulationDispatcher dispatcher;

        SimulationClock(SimulationDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(dispatcher.currentTime);
        }

        @Override
        public long millis() {
            return dispatcher.currentTime;
        }

        @Override
        public String toString() {
            return "SimulationDispatcher.InstantSource[time=" + millis() + "ms]";
        }
    }
}
