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
import org.opendc.simulator.network.utils.flyweight.publics.FW
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification
import org.opendc.simulator.network.utils.statefull.publics.State
import org.opendc.simulator.network.utils.statefull.publics.Stateful

internal interface Port: Notifiable<Port>, Stateful<Port>, IInvalidatable, Launchable {
    val owner: Node
    val speed: DataRate
    val portIdx: Idx
    var rxLink: ReceiveLink?
    var txLink: SendLink?

    var fairnessPolicy: FairnessPolicy

    suspend fun startProcessing()

    suspend fun setTxDemand(txDemand: DataRate, netflow: NetFlow, entryId: IntId? = null): IntId

    /**
     * Not guaranteed to be stable. TOOD: write better
     */
    fun getTxTput(entryId: IntId): DataRate

    interface SetDemand: Notification<Port>, FW<SetDemand> {
        var netFlow: NetFlow
        var newDemand: DataRate
        var entryId: IntId

        companion object : FWId<SetDemand>
    }

    interface StartProcessing: Notification<Port>, IFW<StartProcessing> {

        companion object : FWId<StartProcessing>
    }

    interface Connect: Notification<Port>, IFW<Connect> {
        var other: Port
        var linkBw: DataRate?

        companion object : FWId<Connect>
    }

    interface Disconnect: Notification<Port>, IFW<Disconnect> {

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

