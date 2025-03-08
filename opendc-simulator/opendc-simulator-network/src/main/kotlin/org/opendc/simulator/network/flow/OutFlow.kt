/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.flow

import org.jetbrains.annotations.TestOnly
import org.opendc.common.units.DataRate
import org.opendc.common.units.plus
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.tracker.NodeFlowTracker
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.withWarn
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the outgoing flow with id [id] in a specific node.
 */
internal class OutFlow(
    val id: FlowId,
    private val nodeFlowTracker: NodeFlowTracker,
) : Comparable<OutFlow> {
    /**
     * The sum of the outgoing data rate for flow with [id] on all ports.
     */
    private var _totRateOut: DataRate = DataRate.ZERO
        private set(value) {
            @Suppress("NAME_SHADOWING")
            val value = value.roundToIfWithinEpsilon(DataRate.ZERO)
            with(nodeFlowTracker) { handlePropChange { field = value } }
        }
    val totRateOut: DataRate get() = _totRateOut

    /**
     * **Setter should only be used by [FlowHandler].**
     * Represents the data rate demand (to be forwarded)
     * in a node (the rate received / generated).
     */
    var demand: DataRate = DataRate.ZERO
        set(value) {
            @Suppress("NAME_SHADOWING")
            val newValue = value.roundToIfWithinEpsilon(DataRate.ZERO, 1e-03)
            if (newValue.isZero()) {
                tryUpdtRate(DataRate.ZERO)
            }

            with(nodeFlowTracker) { handlePropChange { field = newValue } }
        }

    /**
     * Maps each port associated with an outgoing path for this flow, to its
     * outgoing data rate for this flow.
     */
    val outRatesByPort: Map<Port, DataRate> get() = _outRatesByPort
    private val _outRatesByPort = ConcurrentHashMap<Port, DataRate>()

    /**
     * Tries to update the outgoing data rate for flow [id] to [newRate].
     * @return the updated data rate, which can be less than or equal to the requested rate.
     */
    fun tryUpdtRate(newRate: DataRate = demand): DataRate {
        @Suppress("NAME_SHADOWING")
        val newRate = newRate.roundToIfWithinEpsilon(DataRate.ZERO)
        if (newRate == totRateOut) return totRateOut

        val deltaRate = newRate - totRateOut
        if (deltaRate < DataRate.ZERO) {
            reduceRate(targetRate = newRate)
        } else {
            tryIncreaseRate(targetRate = newRate)
        }

        updtTotRateOut()
        verify()
        return totRateOut
    }

    /**
     * Tries to update the outgoing data rate for flow [id] on port [port] to rate [newRate].
     * @return the updated data rate on port [port], which can be
     * less than or equal to the requested value.
     */
    fun tryUpdtPortRate(
        port: Port,
        newRate: DataRate,
    ): DataRate {
        @Suppress("NAME_SHADOWING")
        val newRate = newRate.roundToIfWithinEpsilon(DataRate.ZERO)

        return _outRatesByPort.computeIfPresent(port) { _, _ ->
            port.tryUpdtRateOf(fId = id, targetRate = newRate)
        }?.also {
            updtTotRateOut()
        } ?: log.withWarn(
            DataRate.ZERO,
            "trying to update a " +
                "data rate of flow id $id through port $port," +
                " which is not among those used to output this flow",
        )
    }

    /**
     * Sets the outgoing ports ([ports]) for this flow ([id]).
     * [totRateOut] is adjusted following the possible removal of possible outgoing ports.
     */
    fun setOutPorts(ports: Set<Port>) {
        val toRm = outRatesByPort.keys - ports
        toRm.forEach { port ->
            val res = port.tryUpdtRateOf(fId = id, targetRate = DataRate.ZERO)
            check(res.isZero())
            _outRatesByPort.remove(port)
        }

        ports.forEach { _outRatesByPort.putIfAbsent(it, DataRate.ZERO) }

        updtTotRateOut()
    }

    private fun updtTotRateOut() {
        _totRateOut = DataRate.ofKbps(outRatesByPort.values.sumOf { it.toKbps() })
    }

    private fun tryIncreaseRate(targetRate: DataRate) {
        check(targetRate >= totRateOut)
        if (outRatesByPort.isEmpty()) return
        var deltaRemaining: DataRate = targetRate - totRateOut
        val targetPerPort: DataRate = targetRate / outRatesByPort.size
        check(targetPerPort >= DataRate.ZERO) { "${outRatesByPort.size}" }

        // reluctant
        _outRatesByPort.replaceAll { port, rate ->
            if (rate >= targetPerPort) return@replaceAll rate
            val resultingRate = port.tryUpdtRateOf(fId = id, targetPerPort)

            deltaRemaining = (deltaRemaining - (resultingRate - rate)).roundToIfWithinEpsilon(DataRate.ZERO)

            return@replaceAll resultingRate
        }

        if (deltaRemaining.isZero()) return

        // non reluctant
        _outRatesByPort.replaceAll { port, rate ->
            check(rate == port.outgoingRateOf(id)) { "\n $rate ${port.incomingRateOf(id)}" }
            val resultingRate = port.tryUpdtRateOf(fId = id, targetRate = rate + deltaRemaining)

            deltaRemaining = (deltaRemaining - (resultingRate - rate)).roundToIfWithinEpsilon(DataRate.ZERO)
            return@replaceAll resultingRate
        }
    }

    private fun reduceRate(targetRate: DataRate) {
        require(targetRate <= totRateOut)
        val deltaRate = targetRate - totRateOut
        _outRatesByPort.replaceAll { port, currRate ->
            // Each port rate is reduced proportionally to its contribution to the total.
            val portTargetRate = (currRate + deltaRate * (currRate / totRateOut)).roundToIfWithinEpsilon(DataRate.ZERO)
            port.tryUpdtRateOf(fId = id, targetRate = portTargetRate)
        }
    }

    override fun compareTo(other: OutFlow): Int = totRateOut.compareTo(other.totRateOut)

    override fun toString(): String { // TODO: remove/change
        return "[id=$id, dem:$demand, rateOut:$totRateOut]"
    }

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean = this.javaClass == other?.javaClass && this === other

    @TestOnly
    internal fun verify() {
        _outRatesByPort.forEach { (port, rate) ->
            check(port.outgoingRateOf(id) approx rate) { "($id) portRate=${port.outgoingRateOf(id)}, tableRate=$rate" }
        }
    }

    private companion object {
        val log by logger()
    }
}
