package org.opendc.simulator.network

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.selects.select

internal suspend fun main() {
    var collect = 0
    coroutineScope {
        val f = MutableSharedFlow<Unit>()
        f.onSubscription { println("subscription") }
        f.emit(Unit)
        f.emit(Unit)

        select<Unit> {
            f.onEach { value -> println(++collect) }.launchIn(this@coroutineScope)
        }
    }
}

