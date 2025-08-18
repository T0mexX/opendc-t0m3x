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

package org.opendc.simulator.network.components.networks.flatfly

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.networks.NetSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.pow

/**
 * Specification for building a [FlatFly] (*Flatten Butterfly*) network topology.
 *
 * @property n The number of dimensions in the network.
 * @property k The network radix, in this case, the number of routers (switches) per dimension.
 * @property c Concentration of the network, number of
 * endpoints/hosts (also called terminals) connected to each router.
 */
@Serializable
@SerialName("flatfly")
internal data class FlatFlySpecs(
    val n: Int,
    val k: Int,
    val c: Int = k,
    val gwPerc: Percentage,
    // TODO: change how gateway nodes are selected
) : NetSpecs<FlatFly> {
    /**
     * Total radix of a switch (number of ports).
     */
    val r: Int = c + (k - 1) * n

    override val R_: Int = k.toDouble().pow(n).toInt()

    override val N_: Int = R_ * c

    override val V_: Int = R_ + N_

    override val E_: Int = (((k - 1) * n * R_).also {
        assert(it % 2 == 0)
    } / 2) + (c * R_)

    context(NetSimScope)
    override suspend fun build(): FlatFly = FlatFly(this)
}
