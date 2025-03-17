package org.opendc.simulator.network.utils.datastructures


internal class IntArrayQueue(initialCapacity: Int) {
    private var array = IntArray(initialCapacity)
    private var front = 0
    private var rear = -1
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
