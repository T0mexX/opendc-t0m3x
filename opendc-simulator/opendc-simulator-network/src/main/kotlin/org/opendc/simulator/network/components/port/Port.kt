//package org.opendc.simulator.network.components.port
//
//import org.opendc.common.units.DataRate
//import org.opendc.simulator.network.components.link.SendLink
//import org.opendc.simulator.network.components.node.Node
//import org.opendc.simulator.network.flow.internals.INetFlow
//import org.opendc.simulator.network.utils.Idx
//import org.opendc.simulator.network.utils.IntId
//import org.opendc.simulator.network.utils.invalidatable.internals.IInvalidatable
//import org.opendc.simulator.network.utils.notifiable.Msgable
//
//internal interface Port: Msgable<Port>, IInvalidatable {
//    val owner: Node<*>
//    val speed: DataRate
//    val portIdx: Idx
//    var rxLink: ReceiveLink?
//    var txLink: SendLink?
//
//    /**
//     * TODO
//     */
//    suspend fun attemptTx()
//
//    /**
//     * TODO
//     */
//    suspend fun setTentativeTx(tx: DataRate, f: INetFlow)
//
////    /**
////     * TODO
////     */
////    suspend fun msgSetTxDemand(txDemand: DataRate, netF: INetFlow, entryId: IntId? = null): IntId?
//
//    /**
//     * TODO
//     * if port has updates to be processed then this should throw
//     */
//    fun getTxTput(entryId: IntId): DataRate
//
////    interface SetDemand: Msg<Port, SetDemand> {
////        var netF: INetFlow
////        var newDemand: DataRate
////        var entryId: IntId?
////
////        companion object : FWId<SetDemand>
////    }
////
////    interface Process: Msg<Port, Process> {
////
////        companion object : FWId<Process>
////    }
////
////    interface Connect: Msg<Port, Connect> {
////        var other: Port
////        var linkBw: DataRate?
////
////        companion object : FWId<Connect>
////    }
////
////    interface Disconnect: Msg<Port, Disconnect> {
////
////        companion object : FWId<Disconnect>
////    }
////
////    companion object {
////        val STABLE: State<Port> = object : State<Port> {}
////        val PROCESSING: State<Port> = object : State<Port> {}
////        val DISCONNECTED: State<Port> = object : State<Port> {}
////        val IDLE: State<Port> = object : State<Port> {}
////        val CONNECTING: State<Port> = object : State<Port> {}
////        val DISCONNECTING: State<Port> = object : State<Port> {}
////    }
//}
//
