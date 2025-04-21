package org.opendc.simulator.network.utils.evntemitter.publics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.selects.select
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 */
public interface EvntEmitter<T: EvntEmitter<T>> {
    /**
     * @param scope If defined, the collector will be launched in this scope,
     * else it will be launched in [NetSimScope] context parameter.
     *
     * @return A collector that can be used to listen to multiple [EvntFlow]s
     * from multiple [EvntEmitter] at the same time, using [select] clause.
     */
    context(NetSimScope)
    public fun evntCollector(scope: CoroutineScope = this@NetSimScope): EvntCollector<T>
}
