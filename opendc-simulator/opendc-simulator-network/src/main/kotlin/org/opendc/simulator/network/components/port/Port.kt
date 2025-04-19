package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.IntId
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.Msgable
import org.opendc.simulator.network.utils.statefull.State
import org.opendc.simulator.network.utils.statefull.Stateful

internal interface Port: Msgable<Port>, Stateful<Port>, IInvalidatable, Launchable {
    val owner: Node<*>
    val speed: DataRate
    val portIdx: Idx
    var rxLink: ReceiveLink?
    var txLink: SendLink?

    var fairnessPolicy: FairnessPolicy

    /**
     * TODO
     */
    suspend fun msgProcess()

    /**
     * TODO
     */
    suspend fun msgSetTxDemand(txDemand: DataRate, netF: INetFlow, entryId: IntId? = null): IntId?

    /**
     * Not guaranteed to be stable. TOOD: write better
     */
    fun getTxTput(entryId: IntId): DataRate

    interface SetDemand: Msg<Port, SetDemand> {
        var netF: INetFlow
        var newDemand: DataRate
        var entryId: IntId?

        companion object : FWId<SetDemand>
    }

    interface Process: Msg<Port, Process> {

        companion object : FWId<Process>
    }

    interface Connect: Msg<Port, Connect> {
        var other: Port
        var linkBw: DataRate?

        companion object : FWId<Connect>
    }

    interface Disconnect: Msg<Port, Disconnect> {

        companion object : FWId<Disconnect>
    }

    companion object {
        val STABLE: State<Port> = object : State<Port> {}
        val PROCESSING: State<Port> = object : State<Port> {}
        val DISCONNECTED: State<Port> = object : State<Port> {}
        val IDLE: State<Port> = object : State<Port> {}
        val CONNECTING: State<Port> = object : State<Port> {}
        val DISCONNECTING: State<Port> = object : State<Port> {}
    }
}

