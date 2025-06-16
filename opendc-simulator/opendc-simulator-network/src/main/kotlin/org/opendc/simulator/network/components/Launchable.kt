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

package org.opendc.simulator.network.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal interface Launchable {
    /**
     * TODO
     * this scope not necessarily NetSimScope
     * when context receivers are replaced with context parameters,
     * the 2 scopes can both be in the context with assigned names to distinguish.
     *
     * The coroutine context of the launched coroutine should contain a new [CoroutineID].
     *
     * @param scope If defined, the collector will be launched in this scope,
     * else it will be launched in [NetSimScope] context parameter.
     */
    context(NetSimScope)
    suspend fun netLaunch(scope: CoroutineScope = this@NetSimScope): Job
}
