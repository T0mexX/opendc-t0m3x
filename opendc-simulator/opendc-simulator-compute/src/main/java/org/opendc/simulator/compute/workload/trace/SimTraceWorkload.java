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

package org.opendc.simulator.compute.workload.trace;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opendc.simulator.compute.workload.SimWorkload;
import org.opendc.simulator.compute.workload.trace.scaling.NoDelayScaling;
import org.opendc.simulator.compute.workload.trace.scaling.ScalingPolicy;
import org.opendc.simulator.engine.graph.FlowConsumer;
import org.opendc.simulator.engine.graph.FlowEdge;
import org.opendc.simulator.engine.graph.FlowNode;
import org.opendc.simulator.engine.graph.FlowSupplier;
import org.opendc.simulator.engine.graph.NetworkSupplier;
import org.opendc.simulator.network.api.NetIFace;
import org.opendc.simulator.network.api.integration.JNetFTracker;
import org.opendc.simulator.network.api.integration.JNetFlow;
import org.opendc.simulator.network.api.integration.JNetIFace;

public class SimTraceWorkload extends SimWorkload implements FlowConsumer {
    private LinkedList<TraceFragment> remainingFragments;
    private int fragmentIndex;

    private TraceFragment currentFragment;
    private long startOfFragment;

    private FlowEdge machineEdge;

    private double cpuFreqDemand = 0.0; // The Cpu demanded by fragment
    private double cpuFreqSupplied = 0.0; // The Cpu speed supplied
    private double newCpuFreqSupplied = 0.0; // The Cpu speed supplied
    private double remainingWork = 0.0; // The duration of the fragment at the demanded speed

    private final long checkpointDuration;

    private final TraceWorkload snapshot;

    private final ScalingPolicy scalingPolicy;

    private final String taskName;

    private @Nullable JNetIFace netIFace;
    // With the current odc trace format, only total tx and rx of a vm are available.
    // It is assumed that all tx and rx traffic is therefore inter-datacenter, hence
    // 1 flow for transmission and one for reception.
    private @Nullable JNetFlow netFlowTx; // The transmission network flow.
    private @Nullable JNetFlow netFlowRx; // The reception network flow.
    private @Nullable JNetFTracker netFTracker;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Getters and Setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public long getPassedTime(long now) {
        return now - this.startOfFragment;
    }

    public TraceWorkload getSnapshot() {
        return snapshot;
    }

    @Override
    public long getCheckpointInterval() {
        return 0;
    }

    @Override
    public long getCheckpointDuration() {
        return 0;
    }

    @Override
    public double getCheckpointIntervalScaling() {
        return 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimTraceWorkload(FlowSupplier supplier, TraceWorkload workload) {
        super(((FlowNode) supplier).getEngine());
        bo.add(this); // TODO: delete ln

        this.snapshot = workload;
        this.checkpointDuration = workload.checkpointDuration();
        this.scalingPolicy = workload.getScalingPolicy();
        this.remainingFragments = new LinkedList<>(workload.getFragments());
        this.fragmentIndex = 0;
        this.taskName = workload.getTaskName();

        this.startOfFragment = this.clock.millis();

        // If the supplier provides networking (vm either directly or through chainWL),
        // then use its network interface to execute fragments' network requirements.
        NetIFace suppNetIFace = supplier instanceof NetworkSupplier netSupplier ? netSupplier.getNetIFace() : null;
        // Convert [NetIFace] into the java non-suspending adapted version.
        this.netIFace = suppNetIFace != null ? suppNetIFace.getJNetIFace() : null;

        setUpNetworkFlows();

        new FlowEdge(this, supplier);
    }

    void setUpNetworkFlows() {
        if (netIFace == null) return;
        this.netFlowTx = Objects.requireNonNull(netIFace.startFlow());
        this.netFlowRx = Objects.requireNonNull(netIFace.startFlowFromInet());

        final @NotNull JNetFlow tx = Objects.requireNonNull(this.netFlowTx);
        final @NotNull JNetFlow rx = Objects.requireNonNull(this.netFlowRx);

        // If [NoDelay] scaling is used, no need to set up anything else.
        if (this.scalingPolicy.getNetTxCompletionRequired(10) == .0) return;

        this.netFTracker = new JNetFTracker(netIFace, tx, rx);
        // When a one fragment flow completes (either rx or tx), reset its demand to zero.
        this.netFTracker.setOn1FFragCompl((f, fragId) -> {
            // If when this set demand is processed, the fragment has been changed
            // (network fragment completion and next fragment happen in the same update cycle, hence [fragId] differs),
            // the [setDemand] has no effect.
            f.setDemand(.0, fragId);
        });

        // If the estimated time remaining for the current fragment (networking) decreases,
        // invalidate this node.
        this.netFTracker.setOnAllComplTsDecreased((oldMs, newMs) -> invalidate());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fragment related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long onUpdate(long now) {
        long passedTime = getPassedTime(now);
        this.startOfFragment = now;

        assert now == Objects.requireNonNull(netIFace).netSimTmstampLong();

        // The amount of work done since last update
        double finishedWork = this.scalingPolicy.getFinishedWork(this.cpuFreqDemand, this.cpuFreqSupplied, passedTime);

        this.remainingWork -= finishedWork;

        // If this.remainingWork <= 0, the fragment compute part has been completed
        if (this.remainingWork <= 0) {
            long netAllCompl = now;
            long netNextCompl = now;
            if (this.netFTracker != null) {
                netAllCompl = this.netFTracker.tsForAllCompl();
                netNextCompl = this.netFTracker.tsFor1Compl();
                assert netAllCompl >= now;
                assert netNextCompl >= now;
            }

            // If both compute and network work satisfied.
            if (this.netFTracker == null || netAllCompl <= now) {
                this.startNextFragment();

                this.invalidate();
                return Long.MAX_VALUE;

            } else {
                //
                // Compute satisfied but network not yet.

                this.cpuFreqSupplied = this.newCpuFreqSupplied;

                // Set compute demand to 0 while waiting for network.
                this.pushOutgoingDemand(this.machineEdge, .0);

                // TODO remove
//                System.out.println("remTm(sec): " + ((netAllCompl - now) / 1000.0));

                // Returning [netNextCompl] ensures that [NetController.sync] is invoked at that timestamp, so that
                // [NetFTracker.on1FragCompleted] handler is invoked, and the flow demand is set to 0.
                return Long.min(netAllCompl, netNextCompl);
            }
        }

        this.cpuFreqSupplied = this.newCpuFreqSupplied;

        // The amount of time required to finish the fragment at this speed
        long remainingDuration = this.scalingPolicy.getRemainingDuration(
                this.cpuFreqDemand, this.newCpuFreqSupplied, this.remainingWork);

        if (remainingDuration == 0.0) {
            this.remainingWork = 0.0;
        }

        try {
            return Math.addExact(now, remainingDuration);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public TraceFragment getNextFragment() {
        if (this.remainingFragments.isEmpty()) {
            return null;
        }
        this.currentFragment = this.remainingFragments.pop();
        this.fragmentIndex++;

        return this.currentFragment;
    }

    private void startNextFragment() {

        TraceFragment nextFragment = this.getNextFragment();
        if (nextFragment == null) {
            this.stopWorkload();
            return;
        }
        double demand = nextFragment.cpuUsage();
        this.remainingWork = this.scalingPolicy.getRemainingWork(demand, nextFragment.duration());
        this.pushOutgoingDemand(this.machineEdge, demand);
        startNetworkFragment(nextFragment);
    }

    private void startNetworkFragment(TraceFragment fragment) {
        if (netIFace == null) return;
        assert netFlowTx != null;
        assert netFlowRx != null;
        final boolean noDelay = scalingPolicy instanceof NoDelayScaling;

        final double txDmndKbps = fragment.netTxKbps();
        final double rxDmndKbps = fragment.netRxKbps();
        final double fragmentDurationSec = (double) fragment.duration() / 1000;
        final double requiredTxKb = scalingPolicy.getNetTxCompletionRequired(txDmndKbps * fragmentDurationSec);
        final double requiredRxKb = scalingPolicy.getNetRxCompletionRequired(rxDmndKbps * fragmentDurationSec);
        if (!noDelay) {
            netFlowTx.fragInit(requiredTxKb, currentFragment);
            netFlowRx.fragInit(requiredRxKb, currentFragment);
        }
        netFlowTx.setDemand(txDmndKbps, currentFragment);
        netFlowRx.setDemand(rxDmndKbps, currentFragment);
        if (!noDelay) Objects.requireNonNull(netFTracker).newFrag(currentFragment);
    }

    @Override
    public void closeNode() {
        assert bo.remove(this); // TODO: delete ln
        System.out.println("CLOSED NODE");
        if (netIFace != null) {
            Objects.requireNonNull(this.netFTracker).close();
            this.netIFace.stopFlow(Objects.requireNonNull(netFlowTx));
            this.netIFace.stopFlow(Objects.requireNonNull(netFlowRx));
        }
        super.closeNode();
    }

    @Override
    public void stopWorkload() {
        if (this.machineEdge == null) {
            return;
        }

        // TODO: Maybe move this to the end
        // Currently stopWorkload is called twice
        this.closeNode();

        this.machineEdge = null;
        this.remainingFragments = null;
        this.currentFragment = null;
        if (netIFace != null) {
            netFlowTx = null;
            netFlowRx = null;
            netFTracker = null;
            netIFace = null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Checkpoint related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * SimTraceWorkload does not make a checkpoint, checkpointing is handled by SimChainWorkload
     * TODO: Maybe add checkpoint models for SimTraceWorkload
     */
    @Override
    public void createCheckpointModel() {}

    /**
     * Create a new snapshot based on the current status of the workload.
     * @param now Moment on which the snapshot is made in milliseconds
     */
    public void makeSnapshot(long now) {

        // Check if fragments is empty

        // Get remaining time of current fragment
        long passedTime = getPassedTime(now);

        // The amount of work done since last update
        double finishedWork = this.scalingPolicy.getFinishedWork(this.cpuFreqDemand, this.cpuFreqSupplied, passedTime);

        this.remainingWork -= finishedWork;

        // The amount of time required to finish the fragment at this speed
        long remainingTime =
                this.scalingPolicy.getRemainingDuration(this.cpuFreqDemand, this.cpuFreqDemand, this.remainingWork);

        // If this is the end of the Task, don't make a snapshot
        if (this.currentFragment == null || (remainingTime <= 0 && remainingFragments.isEmpty())) {
            return;
        }

        // Create a new fragment based on the current fragment and remaining duration
        TraceFragment newFragment = new TraceFragment(
                remainingTime,
                currentFragment.cpuUsage(),
                currentFragment.coreCount(),
                currentFragment.netTxKbps(),
                currentFragment.netRxKbps());

        // Alter the snapshot by removing finished fragments
        this.snapshot.removeFragments(this.fragmentIndex);
        this.snapshot.addFirst(newFragment);

        this.remainingFragments.addFirst(newFragment);

        // Create and add a fragment for processing the snapshot process
        TraceFragment snapshotFragment = new TraceFragment(
                this.checkpointDuration, this.snapshot.getMaxCpuDemand(), this.snapshot.getMaxCoreCount(), 0, 0);
        this.remainingFragments.addFirst(snapshotFragment);

        this.fragmentIndex = -1;
        startNextFragment();

        this.startOfFragment = now;

        this.invalidate();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowGraph Related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handle updates in supply from the Virtual Machine
     *
     * @param supplierEdge edge to the VM on which this is running
     * @param newSupply The new demand that needs to be sent to the VM
     */
    @Override
    public void handleIncomingSupply(FlowEdge supplierEdge, double newSupply) {
        if (newSupply == this.cpuFreqSupplied) {
            return;
        }

        this.cpuFreqSupplied = this.newCpuFreqSupplied;
        this.newCpuFreqSupplied = newSupply;

        this.invalidate();
    }

    /**
     * Push a new demand to the Virtual Machine
     *
     * @param supplierEdge edge to the VM on which this is running
     * @param newDemand The new demand that needs to be sent to the VM
     */
    @Override
    public void pushOutgoingDemand(FlowEdge supplierEdge, double newDemand) {
        if (newDemand == this.cpuFreqDemand) {
            return;
        }

        this.cpuFreqDemand = newDemand;
        this.machineEdge.pushDemand(newDemand);
    }

    /**
     * Add the connection to the Virtual Machine
     *
     * @param supplierEdge edge to the VM on which this is running
     */
    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.machineEdge = supplierEdge;
    }

    /**
     * Handle the removal of the connection to the Virtual Machine
     * When the connection to the Virtual Machine is removed, the SimTraceWorkload is removed
     *
     * @param supplierEdge edge to the VM on which this is running
     */
    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        if (this.machineEdge == null) {
            return;
        }

        this.stopWorkload();
    }

    @Override
    public Map<FlowEdge.NodeType, List<FlowEdge>> getConnectedEdges() {
        return Map.of(FlowEdge.NodeType.CONSUMING, (this.machineEdge != null) ? List.of(this.machineEdge) : List.of());
    }




    // TODO: DELET
    static Set<SimTraceWorkload> bo = Collections.synchronizedSet(new HashSet<>());
}
