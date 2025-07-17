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

package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.flow.INetFlow
import org.opendc.simulator.network.components.msgable.Msg
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.simscope.FPAHandler.Companion.roundDR
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.datastructures.IntArrayQueue

internal class LinkImpl private constructor(
    override val receiverN: Node<*>,
    override val maxBw: DataRate,
    override val stabilizer: NetSimStabilizer,
    override val linkIdx: Int,
    initialCapacity: Int,
) : Link, SendChannel<Msg<Node<*>, *>> by receiverN.msgChl {
    private var usedBw: DataRate = DataRate.zero
    override val availableBw: DataRate get() = maxBw - usedBw
    override val util: Percentage get() = usedBw / maxBw

    override var totTentativeTx: DataRate = DataRate.zero

    /**
     * Ensure mutual exclusion between the process of
     * attempting tx and to set the tentative tx.
     */
    private val mtx = Mutex()

    context(NetSimScope)
    override suspend fun attemptTx() =
        mtx.withLock {
            assert(usedBw <= maxBw)
            val f = entries.asFlow()
            applyReductions(f)
            applyIncreases(f)

            stabilizer.validate()
            assert(usedBw <= maxBw)
        }

    /**
     * Helper method for [attemptTx]. Applies the tentative tx data rate reductions.
     */
    context(NetSimScope)
    private suspend fun applyReductions(f: Flow<LinkEntry>) =
        coroutineScope {
            f.onEach { e ->
                if (e.used.not()) return@onEach
                var delta = ((maxBw * (e.demand / totTentativeTx)) min e.demand) - e.tput
                delta = delta.roundDR(max = DataRate.zero)
                if (delta >= DataRate.zero) return@onEach
                usedBw = (usedBw + delta).roundDR(min = DataRate.zero)
                e.tput = (e.tput + delta).roundDR(max = e.demand)
                receiverN.msgAsyncRxUpdt(deltaRate = delta, f = e.f)
                if (e.demand approx DataRate.zero) rmEntry(e.idx)
            }.launchIn(this@coroutineScope).join()
        }

    /**
     * Helper method for [attemptTx]. Attempts to apply the tentative tx data rate increases.
     */
    context(NetSimScope)
    private suspend fun applyIncreases(f: Flow<LinkEntry>) =
        coroutineScope {
            f.onEach { e ->
                if (e.used.not()) return@onEach
                val delta = ((maxBw * (e.demand / totTentativeTx)) min e.demand) - e.tput
                if (delta <= DataRate.zero && e.demand == DataRate.zero) return@onEach rmEntry(e.idx)
                if (delta approxSmallerOrEq DataRate.zero) return@onEach
                usedBw = (usedBw + delta).roundDR(max = maxBw)
                e.tput = (e.tput + delta).roundDR(max = e.demand)
                receiverN.msgAsyncRxUpdt(deltaRate = delta, f = e.f)
            }.launchIn(this@coroutineScope).join()
        }

    override suspend fun setTentativeTx(
        dr: DataRate,
        f: INetFlow,
        entryId: Int?,
    ): Int =
        mtx.withLock {
            assert(dr >= DataRate.zero)

            // Invalidate the port until `attemptTx`
            // is called and updates are propagated to `receiverN`.
            stabilizer.invalidate()

            @Suppress("NAME_SHADOWING")
            var entryId = entryId ?: newEntry(f)
            val entry =
                entries[entryId].takeUnless {
                    it.used.not() || (it.isFInit() && it.f !== f)

                    // If entry was removed because the tentative tx at this link was 0, then get a new one.
                } ?: entries[newEntry(f)].also { entryId = it.idx }

            totTentativeTx += dr - entry.demand
            entry.demand = dr

            return entryId
        }

    override fun getTx(entryId: Int): DataRate = entries[entryId].tput

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Link Internal Implementation
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val entries: MutableList<LinkEntry> =
        (0..<initialCapacity).map {
            LinkEntry(it)
        }.toMutableList()

    /**
     * Used for calls to [rmEntry] which may happen in different coroutines.
     */
    private val freeIdxMtx = Mutex()
    private val freeIdxs: IntArrayQueue =
        IntArrayQueue(initialCapacity = initialCapacity).also { q ->
            (0..<initialCapacity).forEach { q.add(it) }
        }

    private fun newEntry(f: INetFlow): Idx =
        let {
            freeIdxs.poll() ?: grow1()
        }.also { idx ->
            entries[idx].used = true
            entries[idx].f = f
        }

    private fun grow1(): Idx {
        entries.add(LinkEntry(idx = entries.size))
        return entries.size - 1
    }

    private suspend fun rmEntry(idx: Idx) =
        freeIdxMtx.withLock {
            entries[idx].used = false
            freeIdxs.add(idx)
        }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            senderN: Node<*>,
            receiverN: Node<*>,
            linkIdx: Int,
        ): Link =
            LinkImpl(
                receiverN = receiverN,
                maxBw = senderN.portSpeed min receiverN.portSpeed,
                stabilizer = barrier.stabilizer(Link::class),
                linkIdx = linkIdx,
                initialCapacity = devConfig.linkConfig.initialCapacity,
            )
    }
}
