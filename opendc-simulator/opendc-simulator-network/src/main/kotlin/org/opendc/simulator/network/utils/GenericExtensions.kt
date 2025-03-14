/*
 * Copyright (c) 2024 AtLarge Research
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

internal fun <T : Comparable<T>> Collection<T>.isSorted(): Boolean {
    if (this.isEmpty()) return true

    var prev: T = this.first()
    this.forEach { curr: T ->
        if (curr < prev) return false
        prev = curr
    }

    return true
}

internal fun <T> Collection<T>.isSorted(comp: Comparator<T>): Boolean {
    if (this.isEmpty()) return true

    var prev: T = this.first()
    this.forEach { curr: T ->
        if (curr.smallerThan(prev, comp)) return false
        prev = curr
    }

    return true
}

private fun <T> T.smallerThan(
    other: T,
    comp: Comparator<T>,
): Boolean = comp.compare(this, other) < 0

internal fun <T> withNotNull(
    with: T,
    block: T.() -> Unit,
) {
    with?.let(block)
}
