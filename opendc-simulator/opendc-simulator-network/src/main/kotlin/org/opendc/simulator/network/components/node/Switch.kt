package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.EnConsumer
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer

internal open class Switch protected constructor(
    ip: IPv4Address,
    override val portSpeed: DataRate,
    override val nPorts: Int,
    override var routPolicy: RoutPolicy,
    override val enModel: EnModel<Switch>,
    override val flowTable: FlowTable,
    override val stabilizer: NetSimStabilizer,
): NodeImpl<Switch>(ip, nPorts), EnConsumer<Switch>, SerializableNode {

    override fun toSpecs(): Specs<Switch> =
        SwitchSpecs(
//            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    override fun toString(): String = "Switch(ip=$ip)"

    companion object {
        context(NetSimScope)
        internal suspend operator fun invoke(
            ip: IPv4Address? = null,
            subnet: IPv4Address? = addrMngr.globalPrefix,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            enModel: EnModel<Switch>? = null,
        ): Switch {
            val nodeConfig = devConfig.nodeConfig
            val switchConfig = nodeConfig.switchConfig

            // Assert both subnet has been claimed.
            subnet?.let { assert(addrMngr.isAddrClaimed(it)) }

            // Assert `ip` is not a subnet.
            ip?.let { assert(it.isPrefixBlock.not()) }

            return Switch(
                ip = ip?.let {
                    // Claim ip.
                    addrMngr.claimIp(ip)
                    // Assert `subnet` is the most specific subnet `ip` is in.
                    assert(addrMngr.getSubnetOf(ip) == subnet)
                    it

                // Retrieve new unused ip address in subnet (subnet can also be 0.0.0.0/0)
                } ?: addrMngr.getNewIp(subNet = subnet),
                portSpeed = portSpeed
                    ?: switchConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: switchConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                routPolicy = this@NetSimScope.config.routPolicy,
                enModel = enModel
                    ?:switchConfig.defaultEnModel
                    ?:nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { s ->
                s.invalidate()
                s.netLaunch()
            }
        }
    }
}
