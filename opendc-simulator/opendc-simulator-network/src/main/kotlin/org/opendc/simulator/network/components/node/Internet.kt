package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.common.units.Power
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.components.networks.NetworkImpl.Companion.INTERNET_ID
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal class Internet(
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
    override val routPolicy: RoutPolicy,
    inetIp: IPv4Address,
): SenderNode<Internet>(inetIp, 0), SerializableNode {

    override val portSpeed: DataRate = DataRate.max
    override var nPorts: Int = 0

    override val links = mutableListOf<Link?>()

    override fun getFreeLinkIdx(): Int =
        links.indexOfFirst {
            it == null
        }.takeIf { it != -1 }
            ?: let {
                links.add(null)
                nPorts++
                links.size - 1
            }

    override fun toSpecs(): Specs<Internet> {
        throw RuntimeException("Internet does not have specs")
    }

    /**
     * TODO
     */
    override val enModel = EnModel<Internet> { Power.zero }

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(): Internet {

            // Claim the ip reserved for the representation
            // of the internet absract node in the network.
            addrMngr.claimIp(INTERNET_ID.toIp())

            return Internet(
                flowTable = devConfig.nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer(),
                routPolicy = config.routPolicy.internetRoutPolicy,
                inetIp = INTERNET_ID.toIp(),
            ).also {
                it.invalidate()
                it.netLaunch()
            }
        }
    }
}
