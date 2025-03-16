package org.opendc.simulator.network.tobedel

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.simscope.rateupdtpool.FlowRateChangePool

internal suspend fun main() {
//    BO.bo()
//    println(BO.boo())
//    println(BO.boo())
    BO.booo()
}



private object BO {

    val f = MutableSharedFlow<DataRate>()

    suspend fun bo() {
        coroutineScope {
            launch {
                while (true) {
                    f.emit(DataRate.ofbps(1))
                    delay(1)
                }
            }

            while (true) {
                delay(1)
            }
        }
    }

    var i = 0
    val m = Mutex()
    suspend fun boo(): Int = m.withLock {
        i++
    }

    suspend fun booo() {
        val pool = FlowRateChangePool(10)
        val idx = pool.getIdx()
        pool.acquire(idx)
        pool.acquire(idx)

    }


}
