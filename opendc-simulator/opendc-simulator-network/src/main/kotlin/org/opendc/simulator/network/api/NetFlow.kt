package org.opendc.simulator.network.api

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.NodeId2
import org.opendc.simulator.network.flow.FlowId2
import org.opendc.simulator.network.utils.eventEmitter.EventEmitter
import org.opendc.simulator.network.utils.notifiable.Notifiable

public interface NetFlow: EventEmitter<NetFlow>, Notifiable<NetFlow> {
    public val id: FlowId2
    public val senderId: NodeId2
    public val destId: NodeId2
    public val demand: DataRate
    public val throughput: DataRate
}
