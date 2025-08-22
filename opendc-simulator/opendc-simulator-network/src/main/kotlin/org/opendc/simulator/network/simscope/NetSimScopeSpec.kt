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

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.api.integration.NetSimGlobal
import org.opendc.simulator.network.export.NetworkExportConfig
import java.io.File
import java.time.InstantSource
import kotlin.random.Random

@Serializable
public data class NetSimScopeSpec internal constructor(
    public val config: NetSimConfig = NetSimConfig(),
    public val netPath: String? = null,
    @Transient internal val injectedTmSrc: NetSimTmSrc.External? = null,
    @Transient internal val injectedInitialTs: Timestamp? = null,
) {
    @Transient private val expConf = config.exportConfig

    internal fun withInjectedExportInterval(td: TimeDelta): NetSimScopeSpec {
        require(NetSimGlobal.WITH_COMPUTE.not())
        requireNotNull(expConf)
        return this.copy(
            config =
                config.copy(
                    exportConfig =
                        expConf.copy(
                            exportInterval = td,
                        ),
                ),
        )
    }

    public fun withInjectedInstantSrc(src: InstantSource): NetSimScopeSpec {
        require(NetSimGlobal.WITH_COMPUTE)
        require(injectedInitialTs == null)
        return this.copy(injectedTmSrc = NetSimTmSrc.External(src))
    }

    public fun withInjectedSeed(seed: Long): NetSimScopeSpec {
        require(NetSimGlobal.WITH_COMPUTE)
        return this.copy(
            config =
                config.copy(
                    seed = seed,
                ),
        )
    }

    public fun withInjectedOutputFolder(outputFolder: File): NetSimScopeSpec {
        require(NetSimGlobal.WITH_COMPUTE)
        requireNotNull(expConf)
        return this.copy(
            config =
                config.copy(
                    exportConfig =
                        expConf.copy(
                            outputFolder = outputFolder,
                        ),
                ),
        )
    }

    public fun withInjectedExportConfig(netExportConfig: NetworkExportConfig): NetSimScopeSpec {
        require(NetSimGlobal.WITH_COMPUTE)
        return this.copy(
            config =
                config.copy(
                    exportConfig = netExportConfig,
                ),
        )
    }

    internal fun withInjectedInitialTmstamp(initialTs: Timestamp): NetSimScopeSpec {
        require(NetSimGlobal.WITH_COMPUTE.not())
        require(injectedTmSrc == null)
        return this.copy(
            injectedInitialTs = initialTs,
        )
    }
}
