package org.opendc.simulator.network.tobedel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch

public suspend fun main() {
    println("A")
        coroutineScope {
            println("B")
            launch {
                println("C")
                try {
                    bo.first { it }
                } finally {
                    println("D")
                }

                println("Ciao")
            }
            delay(1000)
//            bo.value = false
//            bo.emit(true)
            delay(10000)
        }
}

private val bo = MutableStateFlow(true)
