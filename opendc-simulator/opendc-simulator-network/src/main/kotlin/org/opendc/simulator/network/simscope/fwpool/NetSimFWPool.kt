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

package org.opendc.simulator.network.simscope.fwpool

import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Idx
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal class NetSimFWPool : AbstractCoroutineContextElement(Key) {
    private val poolsById = mutableMapOf<FWId<*>, FWPool<*, *>>()

    context(NetSimScope)
    @Suppress("UNCHECKED_CAST")
    fun <T : FW<T>, O : FWId<T>> getOrAdd(
        id: O,
        objConstructor: suspend (FWPool<T, O>, Idx) -> T,
    ): FWPool<T, O> =
        poolsById.getOrPut(id) {
            FWPool(objConstructor = objConstructor)
        } as FWPool<T, O>

    @Suppress("UNCHECKED_CAST")
    fun <T : FW<T>, O : FWId<T>> getPool(id: O): FWPool<T, O> = poolsById[id]!! as FWPool<T, O>

    companion object Key : CoroutineContext.Key<NetSimFWPool>
}
