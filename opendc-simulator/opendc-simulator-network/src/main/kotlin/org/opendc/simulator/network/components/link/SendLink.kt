package org.opendc.simulator.network.components.link

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import org.opendc.common.units.DataRate
import org.opendc.common.units.Percentage
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.flow.internals.INetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.utils.notifiable.Msg
import org.opendc.simulator.network.utils.notifiable.MsgImpl

/**
 * Interface for a unidirectional network communication link from the sender perspective,
 * used to send messages and manage bandwidth between nodes.
 *
 * This interface does not manage per-flow bandwidth allocations.
 * Instead, it focuses on the overall utilization of the link.
 *
 * A [SendLink] can also be used as a [Msg] [Channel] to send msgs directly to the receiver nodes.
 */
internal interface SendLink: SendChannel<Msg<Node<*>, *>> {
    /**
     * The port on the receiver node that this link connects to.
     */
    val receiverPort: Port

    /**
     * The node that owns the [receiverPort], representing the destination of this link.
     */
    val receiverNode: Node<*> get() = receiverPort.owner

    /**
     * The maximum bandwidth capacity of this link.
     */
    val maxBw: DataRate

    /**
     * The currently available (unutilized) bandwidth on this link.
     */
    val availableBw: DataRate

    /**
     * The current utilization of the link, expressed as a percentage of [maxBw].
     */
    val util: Percentage

    /**
     * Increases the current bandwidth usage on this link by the specified amount, [bw],
     * and notifies the receiver node with a delta bandwidth update, attributing the change to [netF].
     *
     * Note: This class does not maintain a mapping between flows and their allocated bandwidth.
     * It solely tracks total bandwidth usage for congestion management.
     *
     * Context parameter [FairnessPolicy] is only present to enforce (almost)
     * the method to be invoked only during fairness policy phase of update processing.
     *
     * @param bw The amount of bandwidth to be claimed.
     * @param netF The flow to which this bandwidth release should be attributed.
     * @throws AssertionError If assertions are enabled (`-ea` VM option) and the amount of
     * bandwidth being claimed is negative.
     */
    context(FairnessPolicy)
    suspend fun claimBw(bw: DataRate, netF: INetFlow): DataRate

    /**
     * Reduces the current bandwidth usage on this link by the specified amount, [bw],
     * and notifies the receiver node with a delta bandwidth update, attributing the change to [netF].
     *
     * Note: This class does not maintain a mapping between flows and their allocated bandwidth.
     * It solely tracks total bandwidth usage for congestion management.
     *
     * Context parameter [FairnessPolicy] is only present to enforce (almost)
     * the method to be invoked only during fairness policy phase of update processing.
     *
     * @param bw The amount of bandwidth to release.
     * @param netF The flow to which this bandwidth release should be attributed.
     * @throws AssertionError If assertions are enabled (`-ea` VM option) and the amount of
     * bandwidth being released is negative or exceeds the bandwidth currently in use.
     */
    context(FairnessPolicy)
    suspend fun releaseBw(bw: DataRate, netF: INetFlow)
}
