package org.opendc.simulator.network.flow.internals

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.flyweight.internals.IFW
import org.opendc.simulator.network.utils.notifiable.publics.Notification

internal interface INetFlow : NetFlow {

    suspend fun setThroughput(newThroughput: DataRate)

    suspend fun increaseThroughputBy(amount: DataRate)

    interface SetThroughput : Notification<NetFlow>, IFW<SetThroughput> {
        var newThroughput: DataRate
        companion object : FWId<SetThroughput>
    }

    interface IncreaseThroughput : Notification<NetFlow>, IFW<IncreaseThroughput> {
        var amount: DataRate
        companion object : FWId<IncreaseThroughput>
    }
}
