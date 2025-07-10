/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.api.integration

import org.apache.logging.log4j.util.BiConsumer
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.flow.NetFlow
import org.opendc.simulator.network.simscope.NetSimScope

@Suppress("SetterBackingFieldAssignment")
public class JNetFTracker private constructor(
    private val tracker: NetFTracker,
    private val scope: NetSimScope,
) : AutoCloseable by tracker {
    public constructor(iFace: JNetIFace, vararg flows: JNetFlow) :
        this(
            tracker = with(iFace.scope) { NetFTracker(*flows.map { it.f }.toTypedArray()) },
            scope = iFace.scope,
        )

    private val jController = scope.jNetController

    public var onAllComplTsIncreased: BiConsumer<Long, Long> = BiConsumer { _, _ -> }
        set(callback) {
            tracker.onAllComplTsIncreased = { old, new ->
                jController!!.callbacksChl.send { callback.accept(old.toEpochMsLong(), new.toEpochMsLong()) }
            }
        }
    public var onAllComplTsDecreased: BiConsumer<Long, Long> = BiConsumer { _, _ -> }
        set(callback) {
            tracker.onAllComplTsDecreased = { old, new ->
                jController!!.callbacksChl.send { callback.accept(old.toEpochMsLong(), new.toEpochMsLong()) }
            }
        }
    public var on1ComplTsIncreased: BiConsumer<Long, Long> = BiConsumer { _, _ -> }
        set(callback) {
            tracker.on1ComplTsIncreased = { old, new ->
                jController!!.callbacksChl.send { callback.accept(old.toEpochMsLong(), new.toEpochMsLong()) }
            }
        }
    public var on1ComplTsDecreased: BiConsumer<Long, Long> = BiConsumer { _, _ -> }
        set(callback) {
            tracker.on1ComplTsDecreased = { old, new ->
                jController!!.callbacksChl.send { callback.accept(old.toEpochMsLong(), new.toEpochMsLong()) }
            }
        }
    public var on1FFragCompl: BiConsumer<JNetFlow, Any?> = BiConsumer { _, _ -> }
        set(callback) {
            tracker.on1FFragCompl = { f, fragId ->
                f as INetFlow
                jController!!.callbacksChl.send { callback.accept(f.jNetFlow!!, fragId) }
            }
        }
    public var onAllFFragCompl: Runnable = Runnable { }
        set(callback) {
            tracker.onAllFFragCompl = {
                jController!!.callbacksChl.send { callback.run() }
            }
        }

    public suspend fun tsFor1Compl(): Long = latched(scope) {
        tracker.tsFor1Compl().toEpochMsLong()
    }
    public suspend fun tmRmFor1Compl(): Long = latched(scope) {
        tracker.tmRmFor1Compl().toMsLong()
    }
    public suspend fun tsForAllCompl(): Long = latched(scope) {
        tracker.tsForAllCompl().toEpochMsLong()
    }
    public suspend fun tmRmForAllCompl(): Long = latched(scope) {
        tracker.tmRmForAllCompl().toMsLong()
    }

    /**
     * TODO
     * To be called after flows have been msged with the new frag msg.
     */
    public fun reset(): Unit =
        latched(scope) {
            tracker.reset()
        }

    public companion object {
        @JvmStatic
        public fun create(
            iFace: JNetIFace,
            vararg flows: NetFlow,
        ): JNetFTracker =
            latched(iFace.scope) {
                JNetFTracker(
                    tracker = NetFTracker(*flows),
                    scope = iFace.scope,
                )
            }
    }
}
