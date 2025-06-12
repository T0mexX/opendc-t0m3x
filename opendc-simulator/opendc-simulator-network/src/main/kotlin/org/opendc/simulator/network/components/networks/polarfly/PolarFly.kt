package org.opendc.simulator.network.components.networks.polarfly

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.HostNode
import org.opendc.simulator.network.components.node.Internet
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.Switch
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.withProgressBar

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class PolarFly private constructor(
    val specs: PolarFlySpecs,
    val racks: List<PolarFlyRack>,
    override val inet: Internet,
): NetworkImpl() {
    override val _nodesById: MutableMap<NodeId, Node<*>> =
        racks.flatMap { it.switches + it.hosts }.plus(inet).associateBy { it.id }.toMutableMap()

    @Suppress("UNCHECKED_CAST")
    override val _sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        _nodesById.filterValues { it is SenderNode<*> } as MutableMap<NodeId, SenderNode<*>>

    override fun toSpecs(): PolarFlySpecs = specs

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            specs: PolarFlySpecs,
        ): PolarFly = withProgressBar(task = "Building PolarFly Network...", max = specs.E_.toLong() + specs.V_) pb@ {
            val wSubnet = TODO()
            val erSpecs = ERSpecs(q = specs.q)



            TODO()
        }
    }

    data class PolarFlyRack(
        val switches: List<Switch>,
        val hosts: List<HostNode>,
    )
}
