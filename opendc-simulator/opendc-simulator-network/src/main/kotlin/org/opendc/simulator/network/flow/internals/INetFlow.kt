package org.opendc.simulator.network.flow.internals

import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node.Connect
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.flow.publics.FlowId
import org.opendc.simulator.network.flow.publics.NetFlow
import org.opendc.simulator.network.policies.routing.RoutMeta
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.Launchable
import org.opendc.simulator.network.utils.flyweight.publics.FWId
import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.Msgable

internal interface INetFlow : NetFlow, Msgable<INetFlow>, Launchable, IInvalidatable {
    var senderNode: SenderNode<*>

    var routMeta: RoutMeta<*>

    /**
     * TODO
     */
    val parentFlow: INetFlow?


    var subFPerc: Percentage?
    /**
     * TODO
     */
    val subFlows: MutableSet<INetFlow>

    /**
     * TODO
     */
    val intermediate: NodeId?

    /**
     * Convenience method to send a [SetThroughput] [Msg] to this [NetFlow].
     * @see SetThroughput
     */
    suspend fun msgAsyncSetTput(newThroughput: DataRate)

    /**
     * Convenience method to send a [IncreaseThroughput] [Msg] to this [NetFlow].
     * @see IncreaseThroughput
     */
    suspend fun msgAsyncIncreaseTputBy(amount: DataRate)

    /**
     * TODO
     * should be called only on flow initialization, hence no need for a [Msg].
     */
    context(NetSimScope)
    suspend fun subFlow(intermediate: NodeId? = null): INetFlow

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
