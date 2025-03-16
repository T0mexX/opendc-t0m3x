package org.opendc.simulator.network.simscope.barrier

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opendc.simulator.network.simscope.NetSimConfig

internal suspend fun main() {
    val bar = NetSimBarrier(NetSimConfig.DEFAULT)
    val stabilizers = 0.rangeTo(1000).map { bar.stabilizer() }

    coroutineScope {
        stabilizers.map { stab ->
            launch {
                repeat(10000) {
                    if (0.rangeTo(2).random() == 1) {
                        stab.validate()
                    } else stab.invalidate()
                }
                stab.validate()
            }
        }
    }
    bar.awaitStability()

}
