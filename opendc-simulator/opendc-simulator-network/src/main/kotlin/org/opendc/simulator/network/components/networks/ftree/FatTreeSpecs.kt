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

package org.opendc.simulator.network.components.networks.ftree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetSpecs
import org.opendc.simulator.network.components.node.switchh.SwitchSpecs
import org.opendc.simulator.network.components.node.terminal.TerminalSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.pow

/**
 * TODO
 * @param k
 */
@Serializable
@SerialName("ftree")
internal data class FatTreeSpecs(
    val name: String = "Default",
    val k: Int,
    val switchSpecs: SwitchSpecs? = null,
    val crSwSpecs: SwitchSpecs = switchSpecs!!,
    val aggrSwSpecs: SwitchSpecs = switchSpecs!!,
    val accessSwSpecs: SwitchSpecs = switchSpecs!!,
    val hostSpecs: TerminalSpecs,
) : NetSpecs<FTree> {
    init {
        require(k % 2 == 0)
    }

    val nCoreSw: Int = (k.toDouble().pow(2.0) / 4).toInt()
    val nAggrSw: Int = (k.toDouble().pow(2.0) / 2).toInt()
    val nEdgeSw: Int = nAggrSw
    val nPods: Int = k
    override val R_: Int = (5 * k.toDouble().pow(2.0) / 4).toInt()
    override val N_: Int = (k.toDouble().pow(3.0) / 4).toInt()
    override val V_: Int = R_ + N_
    override val E_: Int = N_ * 3

    /**
     * Returns a [FTree] if the specs are valid, throws error otherwise.
     */
    context(NetSimScope)
    override suspend fun build(): FTree = FTree(this)
}
