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

package org.opendc.compute.simulator.provisioner

import kotlinx.coroutines.runBlocking
import org.opendc.common.logger.infoNewLine
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.INSTANT
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.NODES
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.export.NetworkExportConfig
import java.io.File

/**
 * Returns a [ProvisioningStep] that sets up the network environment (if any).
 * @param netController The network controller used by the simulation.
 * If `null` network environment is not set up.
 * @param netExportConfig The network export configuration for the simulation.
 * If `null` no network output file will be produced (network related columns can still
 * be present in host, task, service outputs)
 * @param seedOutputFolder The output folder of the current simulation instance.
 * If [netExportConfig] does not provide an output folder, this one is used.
 */
public class NetworkProvisioningStep(
    private val netController: NetworkController? = null,
    private val seedOutputFolder: File,
    netExportConfig: NetworkExportConfig? = null,
) : ProvisioningStep {
    /**
     * The final output folder.
     */
    private val netExportConfig: NetworkExportConfig? =
        netExportConfig?.let { config ->
            config.outputFolder?.let {
                println(it)
                config
            } ?: config.copy(outputFolder = seedOutputFolder)
        }

    override fun apply(ctx: ProvisioningContext): AutoCloseable =
        netController?.let {
            // Sets an external time source for the network simulation,
            // which can be advanced up to the instant source instant with sync()
            netController.setInstantSource(ctx.dispatcher.timeSource)

            // Prints the status of the network.
            LOG.infoNewLine(
                runBlocking {
                    netController.snapshot().fmt(
                        flags =
                            INSTANT +
                                NODES,
                    )
                }
            )

            // Setup network exporting according to netExportConfig.
            netExportConfig?.let {
                netController.setExportConfig(
                    netExportConfig,
                )

                // Logs the setting used for network exporting.
                LOG.infoNewLine(netExportConfig.fmt())
            }

            netController
        } ?: AutoCloseable { }

    private companion object {
        val LOG by logger()
    }
}
