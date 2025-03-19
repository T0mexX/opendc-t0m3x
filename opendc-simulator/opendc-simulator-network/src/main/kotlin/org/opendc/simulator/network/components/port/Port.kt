package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.IntId
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeight
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification
import org.opendc.simulator.network.utils.statefull.publics.State
import org.opendc.simulator.network.utils.statefull.publics.Stateful
import org.opendc.simulator.network.utils.tracker.Tracker

internal interface Port: Notifiable<Port>, Stateful<Port>, Invalidatable, Launchable {
    val owner: Node
    val speed: DataRate
    val portIdx: Idx
    var rxLink: ReceiveLink?
    var txLink: SendLink?

    var fairnessPolicy: FairnessPolicy

    suspend fun startProcessing()

    suspend fun setTxDemand(txDemand: DataRate, netflow: NetFlow, entryId: IntId? = null): IntId

    suspend fun getTxTput(entryId: IntId): DataRate

    interface SetDemand: Notification<Port>, FlyWeight<SetDemand> {
        var netFlow: NetFlow
        var newDemand: DataRate
        var entryId: IntId

        companion object : FlyWeightId<SetDemand>
    }

    interface StartProcessing: Notification<Port>, IFW<StartProcessing> {

        companion object : FlyWeightId<StartProcessing>
    }

    interface Connect: Notification<Port>, IFW<Connect> {
        var other: Port
        var notifyOther: Boolean
        var linkBw: DataRate?

        companion object : FlyWeightId<Connect>
    }

    interface Disconnect: Notification<Port>, IFW<Disconnect> {

        companion object : FlyWeightId<Disconnect>
    }

    companion object {
        val STABLE: State<Port> = object : State<Port> {}
        val PROCESSING: State<Port> = object : State<Port> {}
        val DISCONNECTED: State<Port> = object : State<Port> {}
        val IDLE: State<Port> = object : State<Port> {}
    }
}

