package org.opendc.simulator.network.TOBEDEL

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public fun main() {
    callSuspendFun()
}


private fun callSuspendFun() {
    val coroutineScope = CoroutineScope(Dispatchers.Default)
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            var bo = 0
            while (bo < 10000) bo++
            println("A")
        }
        println("B")
    }
    println("C")
}
