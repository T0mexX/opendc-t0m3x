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

package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.SerialNodeSpecs

/**
 * TODO
 */
internal interface NodeSpecs<T : Node<T>> : SerialNodeSpecs {
    /**
     * TODO
     */
    context(NetSimScope)
    fun nPorts(): Int

    /**
     * TODO
     */
    context(NetSimScope)
    fun portSpeed(): DataRate

    context(NetSimScope)
    suspend fun build(
        subnet: IPv4Address? = null,
        inet: Internet? = null,
        updtRoutTbl: Boolean = true,
    ): T
}
