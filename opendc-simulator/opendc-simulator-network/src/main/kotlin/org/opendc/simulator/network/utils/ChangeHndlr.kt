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

package org.opendc.simulator.network.utils

/**
 * Event handler that can be set to observe properties.
 * @param O The type of the object that contains the property.
 * @param T The type of the property.
 */
public fun interface ChangeHndlr<O, T> {
    /**
     * @param obj The object that contains the property.
     * @param oldValue The old value of the property.
     * @param newValue The new value of the property.
     */
    public fun handle(
        obj: O,
        oldValue: T,
        newValue: T,
    )
}

/**
 * Converts [this]~[ChangeHndlr] into a [LazyChangeHndlr], so that it can be executed later with the same parameters.
 * @see org.opendc.simulator.network.api.integration.SeqComputeIntegration
 */
public fun <O, T> ChangeHndlr<O, T>.lazy(
    obj: O,
    old: T,
    new: T,
): LazyChangeHndlr<O, T> = LazyChangeHndlr(obj, old, new, this)

/**
 * Suspending event handler that can be set to observe properties.
 * @param O The type of the object that contains the property.
 * @param T The type of the property.
 */
public fun interface SusChangeHndlr<O, T> {
    /**
     * @param obj The object that contains the property.
     * @param oldValue The old value of the property.
     * @param newValue The new value of the property.
     */
    public suspend fun handle(
        obj: O,
        oldValue: T,
        newValue: T,
    )
}

/**
 * Lazy [ChangeHndlr].
 * It allows invoking the same handling function
 * later, but with the parameters that would have been used when triggered.
 * @param O The type of the object that contains the property.
 * @param T The type of the property.
 * @param obj The object that contains the property.
 * @param oldValue The oldValue value of the property.
 * @param newValue The newValue value of the property.
 * @param hndlr The handler to be invoked in the future with the aforementioned parameters.
 */
public class LazyChangeHndlr<O, T>(
    private val obj: O,
    private val oldValue: T,
    private val newValue: T,
    private val hndlr: ChangeHndlr<O, T>,
) {
    internal operator fun invoke(): Unit = hndlr.handle(obj, oldValue, newValue)
}
