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

package org.opendc.simulator.network.utils.datastructures

/**
 * A generic, fixed-size multidimensional grid implemented using a flat array.
 *
 * @param T The type of elements stored in the grid.
 * @property dims A list representing the size of each dimension.
 *
 * Features:
 * - Supports accessing and setting values via multi-dimensional indices.
 * - Internally stores data in a 1D array using row-major order.
 * - Provides subgrid iteration starting at a given index prefix.
 *
 * Example:
 * ```
 * val grid = MultiDimGrid<Int>(listOf(3, 4, 5))
 * grid.set(42, 1, 2, 3)
 * println(grid[1, 2, 3]) // Output: 42
 * ```
 */
internal class MultiDimGrid<T> private constructor(
    val dims: List<Int>,
    private val flatten: Array<T?>,
) : Iterable<T?> {
    fun set(
        value: T?,
        vararg indices: Int,
    ) {
        require(indices.size == dims.size)

        val flattenIdx = flattenIdx(*indices)
        flatten[flattenIdx] = value
    }

    operator fun get(vararg indices: Int): T? {
        require(indices.size == dims.size)

        val flattenIdx = flattenIdx(*indices)
        return flatten[flattenIdx]
    }

    fun subGridIterator(vararg indices: Int): Iterator<T?> {
        val nElems = dims.drop(indices.size).reduce(Int::times)
        val subGridStartIdx = flattenIdx(*indices)

        return object : Iterator<T?> {
            var currentIdx = subGridStartIdx
            val until: Int = subGridStartIdx + nElems

            override fun hasNext(): Boolean = currentIdx < until

            override fun next(): T? = flatten[currentIdx++]
        }
    }

    fun subGridSize(vararg indices: Int): Int = dims.drop(indices.size).reduce(Int::times)

    private fun flattenIdx(vararg indices: Int): Int {
        var index = 0
        var multiplier = 1
        var dimIdx: Int
        for (dim in dims.size - 1 downTo 0) {
            dimIdx = if (dim >= indices.size) 0 else indices[dim]
            index += dimIdx * multiplier
            multiplier *= dims[dim]
        }
        return index
    }

    companion object {
        /**
         * Reified constructor to initialize the array to null values.
         */
        inline operator fun <reified T> invoke(dims: List<Int>): MultiDimGrid<T> =
            MultiDimGrid(
                dims = dims,
                flatten = Array(dims.reduce(Int::times)) { null },
            )
    }

    override fun iterator(): Iterator<T?> = flatten.iterator()
}
