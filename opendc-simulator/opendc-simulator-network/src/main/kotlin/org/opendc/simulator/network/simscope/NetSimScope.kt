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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import org.opendc.common.logger.logger
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.components.flow.NetFlowVersion
import org.opendc.simulator.network.components.networks.NetSpecs
import org.opendc.simulator.network.components.networks.Network
import org.opendc.simulator.network.components.networks.custom.CustomNetwork
import org.opendc.simulator.network.components.node.NodeVersion
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.barrier.NetSimBarrier
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.simscope.fwpool.NetSimFWPool
import org.opendc.simulator.network.simscope.ip.NetSimAddressManager
import org.opendc.simulator.network.utils.CoroutineID
import org.opendc.simulator.network.utils.NETWORK_JSON
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Serializable(with = NetSimScope.NetSimScopeSerializer::class)
internal class NetSimScope(
    override var coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : CoroutineScope {
    val ctx: CoroutineContext get() = coroutineContext

    val config: NetSimConfig
    val barrier: NetSimBarrier
    val devConfig: NetSimDevConfig
    val poolAggr: NetSimFWPool
    val idDispenser: NetSimIdDispenser
    val enRecorder: NetSimEnRecorder
    val tmSrc: NetSimTmSrc<*>
    val routPolicy: RoutPolicy
    val log by logger()
    val net: Network<*> get() = _net
    private lateinit var _net: Network<*>
    val addrMngr: NetSimAddressManager

    val nodeVersion: NodeVersion
    val netFlowVersion: NetFlowVersion

    init {
        var tmpCtx = coroutineContext
        runBlocking {
            // Should be at the head of the linked list of the context if added first, and needs to be accessed fast.
            tmpCtx += CoroutineID.new()
            tmpCtx[Job] ?: let { tmpCtx += Job() }
            tmpCtx[NetSimConfig] ?: let { tmpCtx += NetSimConfig() }
            tmpCtx[NetSimBarrier] ?: let {
                tmpCtx += NetSimBarrier(tmpCtx[NetSimConfig]!!)
            }
            tmpCtx[NetSimFWPool] ?: let {
                tmpCtx += NetSimFWPool()
            }
            tmpCtx[NetSimIdDispenser] ?: let { tmpCtx += NetSimIdDispenser() }
            tmpCtx[NetSimTmSrc] ?: let { tmpCtx += NetSimTmSrc.Internal() }
            tmpCtx[NetSimEnRecorder] ?: let { tmpCtx += NetSimEnRecorder(tmpCtx[NetSimTmSrc]!!) }
            tmpCtx[RoutPolicy] ?: let { tmpCtx += tmpCtx[NetSimConfig]!!.routPolicy }
            tmpCtx += NetSimAddressManager()
        }
        coroutineContext = tmpCtx
        config = tmpCtx[NetSimConfig]!!
        barrier = tmpCtx[NetSimBarrier]!!
        devConfig = config.netSimDevConfig
        poolAggr = tmpCtx[NetSimFWPool]!!
        idDispenser = tmpCtx[NetSimIdDispenser]!!
        tmSrc = tmpCtx[NetSimTmSrc]!!
        enRecorder = tmpCtx[NetSimEnRecorder]!!
        routPolicy = tmpCtx[RoutPolicy]!!
        addrMngr = tmpCtx[NetSimAddressManager]!!

        nodeVersion = devConfig.nodeConfig.version
        netFlowVersion = devConfig.netFlowConfig.version
        runBlocking { initDispensers() }
    }

    private suspend fun checkRequirements() =
        with(this) {
            routPolicy.checkRequirements()
        }

    private suspend fun initDispensers() {
        nodeVersion.initDispensers()
        netFlowVersion.initDispensers()
    }

    /**
     * TODO
     */
    internal fun registerNetwork(net: Network<*>) {
        require(::_net.isInitialized.not()) {
            "A network was already registered for this scope"
        }
        _net = net
    }

    /**
     * TODO
     */
    internal suspend fun sync(forceUpdt: Boolean = false) {
        barrier.awaitStability()
        barrier.whileStable(NetSimStabilityMode.CHECKED) {
            enRecorder.sync(forceUpdt)
        }
        // TODO: net.sync
    }

    fun launch(block: suspend NetSimScope.() -> Unit): Job =
        launch(ctx) {
            block()
        }

    companion object {
//        internal fun <T> CoroutineScope.launchNetworkSim(
//            ctx: CoroutineContext = EmptyCoroutineContext,
//            start: CoroutineStart = CoroutineStart.DEFAULT,
//            block: suspend NetSimScope.() -> Unit,
//        ): Job {
//
//            return NetSimScope().launch {
//                with(this as NetSimScope) {
//
//                }
//            }
    }

    /**
     * TODO
     */
    internal class NetSimScopeSerializer : KSerializer<NetSimScope> {
        @Serializable
        private data class Surr(
            val initialTmStamp: Timestamp? = null,
            val netPath: String? = null,
            val netSimConfig: NetSimConfig? = null,
        )

        override val descriptor: SerialDescriptor = serialDescriptor<Surr>()

        @OptIn(ExperimentalSerializationApi::class)
        override fun deserialize(decoder: Decoder): NetSimScope =
            with(decoder.decodeSerializableValue(serializer<Surr>())) {
                var ctx: CoroutineContext = EmptyCoroutineContext

                initialTmStamp?.let {
                    ctx += NetSimTmSrc.Internal(initialTmStamp = it)
                }

                netSimConfig?.let {
                    ctx += it
                }

                return NetSimScope(ctx).also { scope ->

                    with(scope) {
                        runBlocking(scope.ctx) {
                            // If a path to a network topology defined then try to build it.
                            netPath?.let {
                                NetSpecs.fromFile(File(netPath)).build()

                                // Else build an empty modifiable `CustomNetwork` in the scope.
                            } ?: CustomNetwork()

                            scope.checkRequirements()
                        }
                    }
                }
            }

        override fun serialize(
            encoder: Encoder,
            value: NetSimScope,
        ) {
            throw UnsupportedOperationException()
        }
    }
}
