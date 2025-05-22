package org.opendc.simulator.network.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
internal interface Launchable {
    /**
     * TODO
     * this scope not necessarily NetSimScope
     * when context receivers are replaced with context parameters,
     * the 2 scopes can both be in the context with assigned names to distinguish.
     *
     * The coroutine context of the launched coroutine should contain a new [CoroutineID].
     *
     * @param scope If defined, the collector will be launched in this scope,
     * else it will be launched in [NetSimScope] context parameter.
     */
    context(NetSimScope)
    suspend fun netLaunch(scope: CoroutineScope = this@NetSimScope): Job
}
