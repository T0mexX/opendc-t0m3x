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

package org.opendc.simulator.network.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.common.logger.infoNewLine
import org.opendc.common.logger.logger
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.workload.NetworkEvent
import org.opendc.simulator.network.api.workload.SimNetWorkload
import org.opendc.simulator.network.components.networks.`Network.bak`
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.input.readNetworkWl
import org.opendc.simulator.network.utils.CoroutineWorkChannel
import java.io.File
import java.util.UUID
import javax.naming.OperationNotSupportedException
import kotlin.system.measureTimeMillis

/**
 * Represents a runnable scenario which simulates workload [wl]
 * on the network defined by [networkSpecs] and exports results
 * in parquet format based on [exportConfig].
 *
 * @property[networkSpecs]      the specifications of the network on which to run the [wl].
 * @property[wl]                the network workload to simulate.
 * @property[exportConfig]      the settings that determine what, where will be exported and with what frequency.
 * @property[virtualMapping]    flag determining if virtual mapping is to be performed.
 * If virtual mapping is not performed than the node ids in the [wl] need to reflect exactly those in [networkSpecs].
 * If virtual mapping is performed than the nodes in the [wl] are mapped onto physical nodes in the network defined by [networkSpecs]
 */
@Serializable(with = NetworkScenario.NetScenarioSerializer::class)
public data class NetworkScenario(
    val networkSpecs: Specs<Network>,
    val wl: SimNetWorkload,
    private val exportConfig: NetworkExportConfig? = null,
    val virtualMapping: Boolean = true,
) : Runnable {
    // If start time not defined, first workload deadline is used.
    public val exportConfiguration: NetworkExportConfig? =
        exportConfig?.let { config ->
            config.startTime?.let {
                config
            } ?: config.copy(startTime = Timestamp.ofInstant(wl.startInstant))
        }

    init {
        exportConfiguration?.let { log.infoNewLine(it.fmt()) }
    }

    override fun run() {
        log.infoNewLine(wl.fmt())
        RunInstance()
    }

    private inner class RunInstance(
        runName: String = "unnamed-run-${UUID.randomUUID().toString().substring(0, 4)}",
    ) : AutoCloseable {
        val network: Network = networkSpecs.build()
        val runWl: SimNetWorkload = wl.copy()
        val pb: ProgressBar = getProgressBar()
        val netController: NetworkController =
            NetworkController(
                network = network,
                exportConfig =
                    exportConfiguration?.copy(
                        outputFolder = File(exportConfig?.outputFolder?.absolutePath, runName),
                    ),
            )

        init {
            if (virtualMapping) runWl.performVirtualMappingOn(netController)
            // Sets controller time to the instant of the first network event.
            netController.setInternalTime(
                Timestamp.ofInstant(runWl.startInstant),
            )

            netController.execWl(runWl)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun NetworkController.execWl(runWl: SimNetWorkload) =
            runBlocking(network.validator) {
                val chl = CoroutineWorkChannel<NetworkEvent>(scope = this) { it.execIfNotPassed() }
                network.awaitStability()
                val simTime: TimeDelta =
                    TimeDelta.ofMillis(
                        measureTimeMillis {
                            with(runWl) wl@{
                                while (runWl.hasNext()) {
                                    val nextWlDeadline = runWl.peek().deadline
                                    pb.stepBy(execUntil(nextWlDeadline, chl))
                                    while (!chl.isEmpty) yield()
                                    network.awaitStability()
                                }
                            }
                        },
                    )

                pb.refresh()
                println()
                log.info("Simulation time: $simTime")
                this@RunInstance.close()
                chl.close()
            }

        private fun getProgressBar(): ProgressBar =
            ProgressBarBuilder()
                .setInitialMax(wl.numRemainingEvents.toLong())
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Simulating network...")
                .build()

        override fun close() {
            netController.close()
        }
    }

    internal class NetScenarioSerializer : KSerializer<NetworkScenario> {
        @Serializable
        private data class NetScenarioSurrogate(
            val networkSpecsPath: String,
            val wlPath: String,
            val exportConfig: NetworkExportConfig? = null,
            val virtualMapping: Boolean = true,
        )

        private val surrogateSerial: KSerializer<NetScenarioSurrogate> = kotlinx.serialization.serializer()

        override val descriptor: SerialDescriptor = serialDescriptor<NetScenarioSurrogate>()

        override fun deserialize(decoder: Decoder): NetworkScenario {
            val surrogate: NetScenarioSurrogate = decoder.decodeSerializableValue(surrogateSerial)

            return NetworkScenario(
                networkSpecs = Specs.fromFile(surrogate.networkSpecsPath),
                wl = readNetworkWl(surrogate.wlPath),
                exportConfig = surrogate.exportConfig,
                virtualMapping = surrogate.virtualMapping,
            )
        }

        override fun serialize(
            encoder: Encoder,
            value: NetworkScenario,
        ) {
            throw OperationNotSupportedException()
        }
    }

    internal companion object {
        private val log by logger()
    }
}
