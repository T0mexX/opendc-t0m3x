package org.opendc.simulator.network.utils

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * TODO
 */
internal class CoroutineID: AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<CoroutineID>
}
