package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink2
import org.opendc.simulator.network.components.link.SendLink2
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.utils.Idx
import org.opendc.simulator.network.utils.IntId
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeight
import org.opendc.simulator.network.utils.flyweight.publics.FlyWeightId
import org.opendc.simulator.network.utils.invalidatable.internals.Invalidatable
import org.opendc.simulator.network.utils.notifiable.publics.Notifiable
import org.opendc.simulator.network.utils.notifiable.publics.Notification
import org.opendc.simulator.network.utils.statefull.publics.State
import org.opendc.simulator.network.utils.statefull.publics.Stateful

internal interface Port: Notifiable<Port>, Stateful<Port>, Invalidatable, Launchable {
    var rxLink: ReceiveLink2?
    var txLink: SendLink2?

    var fairnessPolicy: FairnessPolicy

    suspend fun startProcessing()

    suspend fun setTxDemand(txDemand: DataRate, entryId: IntId? = null): IntId

    interface SetDemand: Notification<Port>, FlyWeight<SetDemand> {
        var newDemand: DataRate
        var entryId: IntId

        companion object : FlyWeightId<SetDemand>
    }

    interface StartProcessing: Notification<Port>, FlyWeight<StartProcessing> {

        companion object : FlyWeightId<StartProcessing>
    }

    companion object {
        val STABLE: State<Port> = object : State<Port> {}
        val PROCESSING: State<Port> = object : State<Port> {}
        val DISCONNECTED: State<Port> = object : State<Port> {}
        val IDLE: State<Port> = object : State<Port> {}
    }
}

