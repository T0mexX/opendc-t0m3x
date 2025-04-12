package org.opendc.simulator.network.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.onEach
import org.opendc.common.units.Energy
import org.opendc.common.units.Power
import org.opendc.common.units.TimeDelta
import org.opendc.common.units.Timestamp
import org.opendc.simulator.network.components.networks.Network.Companion.getNodesById
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeAsync
import org.opendc.simulator.network.simscope.NetSimScope.Companion.scopeLaunch
import org.opendc.simulator.network.simscope.NetSimTmSrc
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.simulator.network.utils.sync.Synchronizable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class NetSimEnRecorder(
    tmSrc: NetSimTmSrc<*>,
) : Synchronizable<NetSimEnRecorder>, AbstractCoroutineContextElement(Key) {
    private var avrgPwrDraw: Power = Power.zero
    private var currPwrDraw: Power = Power.zero
    private var totEnCons: Energy = Energy.zero
    override var lastSync: Timestamp = tmSrc.initialTmStamp

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun getAvrgPwrDraw(): Power = barrier.whileStable { sync().avrgPwrDraw }

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun getTotEnCons(): Energy = barrier.whileStable { sync().totEnCons }

    /**
     * TODO
     */
    context(NetSimScope)
    suspend fun getCurrPwrDraw(): Power = barrier.whileStable { sync().currPwrDraw }

    /**
     * TODO
     */
    context(NetSimScope)
    override suspend fun sync(): NetSimEnRecorder {
        if (isSync()) return this

        val sinceSync = tmSrc.tmstamp timeDelta lastSync
        check(sinceSync >= TimeDelta.zero)

        val sinceStart = tmSrc.sinceStart
        check(sinceStart >= TimeDelta.zero)

        val startToSync = sinceStart - sinceSync
        check(startToSync >= TimeDelta.zero)

        barrier.whileStable {
            currPwrDraw = compCurrPwrDraw()
            net.nodesById.values.asFlow()

            // Update total energy consumption.
            totEnCons += currPwrDraw * sinceSync

            // Update average power usage.
            avrgPwrDraw = (
                (avrgPwrDraw * startToSync) +
                    currPwrDraw * sinceSync
                ) / sinceStart
        }

        return this
    }

    /**
     * TODO
     */
    context(NetSimScope)
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun compCurrPwrDraw(): Power = scopeAsync {
        barrier.whileStable {
            net.nodesById.values.asFlow().filterIsInstance<EnConsumer<*>>().flatMapMerge {
                flow { emit(it.computePwrDraw()) }
            }.fold(Power.zero) { acc, partial -> acc + partial }
        }
    }.await()

    /**
     * @return a formatted [String] that displays energy consumption and power draw of the network.
     * Preferably to be logged on a new line.
     */
    internal fun fmt(flags: Flags<NetSimEnRecorder> = Flags.all()): String =
        buildString {
            appendLine("| ==== Energy Report ====")
            flags.ifSet(PWR_DRAW) { appendLine("| Current Power Draw: $currPwrDraw") }
            flags.ifSet(AVG_PWR_DRAW) { appendLine("| Average Power Draw: $avrgPwrDraw") }
            flags.ifSet(EN_CONS) { appendLine("| Total Energy Consumed: $totEnCons") }
        }

    companion object Key : CoroutineContext.Key<NetSimEnRecorder> {
        val AVG_PWR_DRAW: Flag<NetSimEnRecorder> = Flag()
        val PWR_DRAW: Flag<NetSimEnRecorder> = Flag()
        val EN_CONS: Flag<NetSimEnRecorder> = Flag()
    }
}
