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
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
public value class NodeId(public val value: UInt) : Comparable<NodeId> {
    internal operator fun inc(): NodeId = NodeId(this.value + 1U)

    override operator fun compareTo(other: NodeId): Int = this.value.compareTo(other.value)

    override fun toString(): String = value.toString()

    internal fun toIp(): IPv4Address = IPv4Address(value.toInt())

    public companion object {
//        public val INVALID: NodeId = NodeId(-1)
        public operator fun invoke(value: Long): NodeId = NodeId(value.toUInt())

        public fun IPv4Address.toNId(): NodeId = NodeId(this.longValue().toUInt())
    }
}
