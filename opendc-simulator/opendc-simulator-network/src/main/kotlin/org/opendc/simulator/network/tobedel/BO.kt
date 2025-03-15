package org.opendc.simulator.network.tobedel

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

public suspend fun main() {
    coroutineScope {
        launch {
            bo.collect()
            println("Ciao")
        }
        bo.value = true
        delay(100000)
    }
}

private val bo = MutableStateFlow(true)

