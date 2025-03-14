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

@file:Suppress("PropertyName")

package org.opendc.simulator.network.components.networks.dragonfly

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetSpecs
import org.opendc.simulator.network.components.node.switchh.SwitchSpecs
import org.opendc.simulator.network.components.node.terminal.TerminalSpecs
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * @param a Number of routers in each group.
 * @param g Number of groups in the network.
 * @param p Number of terminals connected to each router.
 * @param h Number of intergroup connections for each router.
 * Default is maximum given the other parameters.
 *
 * Source: https://dl.acm.org/doi/abs/10.1145/1394608.1382129
 *
 * The names of the parameters are those used in the paper.
 */
@Serializable
@SerialName("dragonfly")
internal data class DFSpecs(
    val a: Int,
    val p: Int = a / 2,
    val h: Int = a / 2,
    val g: Int = a * h + 1,
    val globalSwitchesPerGroup: Int = 1,
    val switchSpecs: SwitchSpecs,
    val hostSpecs: TerminalSpecs,
) : NetSpecs<DragonFly> {
    override val R_: Int = a * g

    override val N_: Int = a * p * g

    override val V_: Int = R_ + R_ * p

    override val E_: Int =
        let {
            val intraGroupsSw2Sw = (a * (a - 1) / 2) * g
            val intraGroupH2Sw = a * p * g
            val interGroup = R_ * h / 2

            intraGroupH2Sw + intraGroupsSw2Sw + interGroup
        }

    /**
     * Property of dragonfly. `true` if there is exactly one connection between each pair of groups
     */
    val isMaxSize: Boolean = N_ == a * p * (a * h + 1)

    init {
        // "The network should be balanced so that a ≥ 2h, 2p ≥ 2h"
        require(a >= 2 * h && 2 * p >= 2 * h)
        // The number of global switches (inet connection) per group needs
        // to be lower or equal to the number of switches in each group.
        require(globalSwitchesPerGroup <= a)

        require((a * g) % 2 == 0)
    }

    context(NetSimScope)
    override suspend fun build(): DragonFly = DragonFly(specs = this)
}
