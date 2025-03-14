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

/**
 * Represents the different coroutines types that run the network simulation.
 */
internal enum class NetCo {
    FLOW,
    NODE,
    MAIN,
    EXTERNAL,
    FLOW_TRACKER,
    ANY,
}

/**
 * Explicitly says what coroutine owns this class instance.
 * If not otherwise specified, all methods and properties are
 * assumed to be invoked/accessed by the [NetRunnable] owner's coroutine.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NetCoOwner(val owner: NetCo, val additionalInfo: String = "")

/**
 * Indicates that the annotated method is safe to be invoked from a different coroutine than [NetCoOwner.owner]'s.
 * @property callableBy The coroutine types that can invoked this method.
 * @property additionalInfo Used to specify in which context and specifically by which coroutine, this method should be invoked.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NonOwnerMethod(val callableBy: Array<NetCo>, val additionalInfo: String = "")

/**
 * Indicates that the annotated property is safe to be accessed from a different coroutine than [NetCoOwner.owner]'s.
 * @property writableBy The coroutine types that can write this property.
 * @property readableBy The coroutine types that can read this property.
 * @property additionalInfo Used to specify in which context and specifically by which coroutine, this property should be accessed.
 */

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NonOwnerProperty(
    val writableBy: Array<NetCo> = [],
    val readableBy: Array<NetCo> = [],
    val additionalInfo: String = "",
)
