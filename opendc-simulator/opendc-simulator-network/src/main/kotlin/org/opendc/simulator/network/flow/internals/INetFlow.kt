package org.opendc.simulator.network.flow.internals

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.evntemitter.publics.IEvntEmitter
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.Msgable

internal interface INetFlow : NetFlow, Msgable<INetFlow>, Launchable, IInvalidatable {
    var senderNode: SenderNode<*>

    suspend fun setThroughput(newThroughput: DataRate)

    suspend fun msgAsyncIncreaseTputBy(amount: DataRate)

    interface SetThroughput : Msg<INetFlow, SetThroughput> {
        var newThroughput: DataRate
        companion object : FWId<SetThroughput>
    }

    interface IncreaseThroughput : Msg<INetFlow, IncreaseThroughput> {
        var amount: DataRate
        companion object : FWId<IncreaseThroughput>
    }

    interface SetDemand: Msg<INetFlow, SetDemand> {
        var newDemand: DataRate
        companion object : FWId<SetDemand>
    }
}
