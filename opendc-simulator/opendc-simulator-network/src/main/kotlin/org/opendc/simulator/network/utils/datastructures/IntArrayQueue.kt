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

internal class IntArrayQueue(initialCapacity: Int) {
    private var array = IntArray(initialCapacity)
    private var front = 0
    private var rear = initialCapacity - 1
    private var size = 0

    fun isEmpty(): Boolean = size == 0

    fun getSize(): Int = size

    private fun isFull(): Boolean = size == array.size

    private fun resize() {
        val newCapacity = array.size * 2
        val newArray = IntArray(newCapacity)

        for (i in 0 until size) {
            newArray[i] = array[(front + i) % array.size]
        }

        array = newArray
        front = 0
        rear = size - 1
    }

    fun add(value: Int) {
        if (isFull()) {
            resize()
        }
        rear = (rear + 1) % array.size
        array[rear] = value
        size++
    }

    fun poll(): Int? {
        if (isEmpty()) {
            return null
        }
        val value = array[front]
        front = (front + 1) % array.size
        size--
        return value
    }

    fun peek(): Int? {
        if (isEmpty()) {
            return null
        }
        return array[front]
    }
}
