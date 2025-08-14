package org.opendc.simulator.network.simscope.barrier

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.system.exitProcess


internal suspend fun main() = coroutineScope {
    val bo = object {}
    val bo2 = object {}
    val mtx = Mutex()
    mtx.lock(bo)
    mtx.lock(bo)
    mtx.unlock()
//    launch {
//        assert()
//    }.join()
    exitProcess(0)

    launch {
        delay(1000)
        mtx.unlock(bo)
    }

    delay(2000)
    assert(!mtx.isLocked)
}
