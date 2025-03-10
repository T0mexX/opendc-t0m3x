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

@file:JvmName("ComputeSteps")

package org.opendc.compute.simulator.provisioner

import org.opendc.compute.simulator.scheduler.ComputeScheduler
import org.opendc.compute.simulator.telemetry.ComputeMonitor
import org.opendc.compute.simulator.telemetry.OutputFiles
import org.opendc.compute.topology.specs.ClusterSpec
import org.opendc.compute.topology.specs.HostSpec
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.export.NetworkExportConfig
import java.io.File
import java.time.Duration

/**
 * Return a [ProvisioningStep] that provisions a [ComputeService] without any hosts.
 *
 * @param serviceDomain The domain name under which to register the compute service.
 * @param scheduler A function to construct the compute scheduler.
 * @param schedulingQuantum The scheduling quantum of the compute scheduler.
 */
public fun setupComputeService(
    serviceDomain: String,
    scheduler: (ProvisioningContext) -> ComputeScheduler,
    schedulingQuantum: Duration = Duration.ofSeconds(1),
    maxNumFailures: Int = 10,
): ProvisioningStep {
    return ComputeServiceProvisioningStep(serviceDomain, scheduler, schedulingQuantum, maxNumFailures)
}

/**
 * Return a [ProvisioningStep] that installs a [ComputeMetricReader] to periodically collect the metrics of a
 * [ComputeService] and report them to a [ComputeMonitor].
 *
 * @param serviceDomain The service domain at which the [ComputeService] is located.
 * @param monitor The [ComputeMonitor] to install.
 * @param exportInterval The interval between which to collect the metrics.
 */
public fun registerComputeMonitor(
    serviceDomain: String,
    monitor: ComputeMonitor,
    exportInterval: Duration = Duration.ofMinutes(5),
    startTime: Duration = Duration.ofMillis(0),
    filesToExport: Map<OutputFiles, Boolean> =
        mapOf(
            OutputFiles.HOST to true,
            OutputFiles.TASK to true,
            OutputFiles.SERVICE to true,
            OutputFiles.POWER_SOURCE to true,
            OutputFiles.BATTERY to true,
        ),
): ProvisioningStep {
    return ComputeMonitorProvisioningStep(serviceDomain, monitor, exportInterval, startTime, filesToExport)
}

/**
 * Return a [ProvisioningStep] that sets up the specified list of hosts (based on [specs]) for the specified compute
 * service.
 *
 * @param serviceDomain The domain name under which the compute service is registered.
 * @param specs A list of [HostSpec] objects describing the simulated hosts to provision.
 * @param optimize A flag to indicate that the CPU resources of the host should be merged into a single CPU resource.
 */
public fun setupHosts(
    serviceDomain: String,
    specs: List<ClusterSpec>,
    startTime: Long = 0L,
    networkController: NetworkController? = null,
): ProvisioningStep {
    return HostsProvisioningStep(serviceDomain, specs, startTime, networkController)
}

/**
 * Returns a [ProvisioningStep] that sets up the network environment (if any).
 * @param networkController the network controller used by the simulation.
 * If `null` network environment is not set up.
 * @param networkExportConfig the network export configuration for the simulation.
 * If `null` no network output file will be produced (network related columns can still
 * be present in host, task, service outputs)
 * @param runOutputFolder the output folder of the current simulation instance.
 * If [networkExportConfig] does not provide an output folder, this one is used.
 */
public fun setUpNetwork(
    networkController: NetworkController? = null,
    networkExportConfig: NetworkExportConfig? = null,
    runOutputFolder: File,
): ProvisioningStep =
    NetworkProvisioningStep(
        netController = networkController,
        netExportConfig = networkExportConfig,
        seedOutputFolder = runOutputFolder,
    )
