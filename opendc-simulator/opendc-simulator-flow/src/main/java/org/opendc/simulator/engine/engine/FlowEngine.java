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

package org.opendc.simulator.engine.engine;

import java.time.Clock;
import java.time.InstantSource;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.Nullable;
import org.opendc.common.Dispatcher;
import org.opendc.simulator.engine.graph.FlowNode;
import org.opendc.simulator.network.api.integration.JNetController;

/**
 * A {@link FlowEngine} simulates a generic flow network.
 * <p>
 * The engine centralizes the scheduling logic of state updates of flow connections, allowing update propagation
 * to happen more efficiently. and overall, reducing the work necessary to transition into a steady state.
 */
public final class FlowEngine implements Runnable {
    /**
     * The queue of {@link FlowNode} updates that need to be updated in the current cycle.
     */
    private final FlowCycleQueue cycleQueue = new FlowCycleQueue(256);

    /**
     * A priority queue containing the {@link FlowNode} updates to be scheduled in the future.
     */
    private final FlowEventQueue eventQueue = new FlowEventQueue(256);

    /**
     * The stack of engine invocations to occur in the future.
     */
    private final InvocationStack futureInvocations = new InvocationStack(256);

    /**
     * A flag to indicate that the engine is active.
     */
    private boolean active;

    private final Dispatcher dispatcher;
    private final InstantSource clock;

    private final @Nullable JNetController netController;

    /**
     * Create a new {@link FlowEngine} instance using the specified {@link CoroutineContext} and {@link InstantSource}.
     */
    public static FlowEngine create(Dispatcher dispatcher, @Nullable JNetController netController) {
        return new FlowEngine(dispatcher, netController);
    }

    public static FlowEngine create(Dispatcher dispatcher) {
        return new FlowEngine(dispatcher, null);
    }

    FlowEngine(Dispatcher dispatcher, @Nullable JNetController netController) {
        this.dispatcher = dispatcher;
        this.clock = dispatcher.getTimeSource();
        this.netController = netController;
    }

    /**
     * Obtain the (virtual) {@link Clock} driving the simulation.
     */
    public InstantSource getClock() {
        return clock;
    }

    /**
     * Enqueue the specified {@link FlowNode} to be updated immediately during the active engine cycle.
     * <p>
     * This method should be used when the state of a flow context is invalidated/interrupted and needs to be
     * re-computed.
     */
    public void scheduleImmediate(long now, FlowNode ctx) {
        scheduleImmediateInContext(ctx);

        // In-case the engine is already running in the call-stack, return immediately. The changes will be picked
        // up by the active engine.
        if (active) {
            return;
        }

        trySchedule(futureInvocations, now, now);
    }

    /**
     * Enqueue the specified {@link FlowNode} to be updated immediately during the active engine cycle.
     * <p>
     * This method should be used when the state of a flow context is invalidated/interrupted and needs to be
     * re-computed.
     * <p>
     * This method should only be invoked while inside an engine cycle.
     */
    public void scheduleImmediateInContext(FlowNode ctx) {
        cycleQueue.add(ctx);
    }

    /**
     * Enqueue the specified {@link FlowNode} to be updated at its updated deadline.
     */
    public void scheduleDelayed(FlowNode ctx) {
        scheduleDelayedInContext(ctx);

        // In-case the engine is already running in the call-stack, return immediately. The changes will be picked
        // up by the active engine.
        if (active) {
            return;
        }

        long deadline = eventQueue.peekDeadline();
        if (deadline != Long.MAX_VALUE) {
            trySchedule(futureInvocations, clock.millis(), deadline);
        }
    }

    /**
     * Enqueue the specified {@link FlowNode} to be updated at its updated deadline.
     * <p>
     * This method should only be invoked while inside an engine cycle.
     */
    public void scheduleDelayedInContext(FlowNode ctx) {
        FlowEventQueue eventQueue = this.eventQueue;
        eventQueue.enqueue(ctx);
    }

    /**
     * Run all the enqueued actions for the specified timestamp (<code>now</code>).
     */
    private void doRunEngine(long now) {
        final FlowCycleQueue cycleQueue = this.cycleQueue;
        final FlowEventQueue eventQueue = this.eventQueue;

        try {
            // Mark the engine as active to prevent concurrent calls to this method
            active = true;

            // Sync network with simulation virtual time.
//            System.out.print("C"); // TODO: delete ln
            if (netController != null) netController.sync();
//            System.out.print("D\n"); // TODO: delete ln

            // Execute all scheduled updates at current timestamp
            while (true) {
                final FlowNode ctx = eventQueue.poll(now);
                if (ctx == null) {
                    break;
                }

                ctx.update(now);
            }

            // Execute all immediate updates
            while (true) {
                final FlowNode ctx = cycleQueue.poll();
                if (ctx == null) {
                    if (netController != null) {
//                                                System.out.print("A");
                        netController.sync(); // Wait until the network is stable.
//                                                System.out.print("B\n");
                        // Execute observers' handlers sequentially, since node invalidation must be sequential.
                        int callbacksExecuted = netController.execCallbacks();
                        // If at least one callback has been executed, there may be an invalidated node.
                        if (callbacksExecuted > 0) continue;
                    }
                    break;
                }

                ctx.update(now);
                //                System.out.print("|");
            }
        } finally {
            active = false;
        }

        // Schedule an engine invocation for the next update to occur.
        long headDeadline = eventQueue.peekDeadline();
        if (headDeadline != Long.MAX_VALUE && headDeadline >= now) {
            trySchedule(futureInvocations, now, headDeadline);
        }
    }

    @Override
    public void run() {
        doRunEngine(futureInvocations.poll());
    }

    /**
     * Try to schedule an engine invocation at the specified [target].
     *
     * @param scheduled The queue of scheduled invocations.
     * @param now The current virtual timestamp.
     * @param target The virtual timestamp at which the engine invocation should happen.
     */
    private void trySchedule(InvocationStack scheduled, long now, long target) {
        // Only schedule a new scheduler invocation in case the target is earlier than all other pending
        // scheduler invocations
        if (scheduled.tryAdd(target)) {
            dispatcher.schedule(target - now, this);
        }
    }
}
