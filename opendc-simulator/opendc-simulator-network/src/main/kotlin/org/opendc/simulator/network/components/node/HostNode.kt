package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

/**
 * TODO
 */
internal class HostNode private constructor(
    ip: IPv4Address,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var routPolicy: RoutPolicy,
    override var enModel: EnModel<HostNode>,
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
) : SenderNode<HostNode>(ip, nPorts), SerializableNode {

    fun bo() {
        enModel.computeCurrConsumpt(this)
    }


    override fun toSpecs(): Specs<HostNode> =
        HostNodeSpecs(
//            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    override fun toString(): String = "HostNode(ip=$ip)"

    companion object {
        context(NetSimScope)
        suspend operator fun invoke(
            ip: IPv4Address? = null,
            subnet: IPv4Address? = addrMngr.globalPrefix,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            enModel: EnModel<HostNode>? = null,
        ): HostNode {
            val nodeConfig = devConfig.nodeConfig
            val hostConfig = nodeConfig.hostNodeConfig

            // Assert both subnet has been claimed.
            subnet?.let { assert(addrMngr.isAddrClaimed(it)) }

            // Assert `ip` is not a subnet.
            ip?.let { assert(it.isPrefixBlock.not()) }

            return HostNode(
                ip = ip?.let {
                    // Claim ip.
                    addrMngr.claimIp(ip)
                    // Assert `subnet` is the most specific subnet `ip` is in.
                    assert(addrMngr.getSubnetOf(ip) == subnet)
                    it

                // Retrieve new unused ip address in subnet (subnet can also be 0.0.0.0/0)
                } ?: addrMngr.getNewIp(subNet = subnet),
                portSpeed = portSpeed
                    ?: hostConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: hostConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                routPolicy = this@NetSimScope.config.routPolicy,
                enModel = enModel
                    ?:hostConfig.defaultEnModel
                    ?:nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { h ->
                h.invalidate()
                h.netLaunch()
            }
        }
    }
}
