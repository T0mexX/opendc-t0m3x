package org.opendc.simulator.network.utils

import kotlinx.coroutines.Job
import org.opendc.simulator.network.simscope.NetSimScope

internal fun interface Launchable {
    context(NetSimScope)
    fun netLaunch(): Job
}
