package org.opendc.simulator.network.components.node

import inet.ipaddr.ipv4.IPv4Address
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.node.internals.flowtable.FlowTable
import org.opendc.simulator.network.components.specs.GlobalSwitchSpecs
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilizer
import kotlin.system.measureTimeMillis

internal class GlobalSwitch private constructor(
    ip: IPv4Address,
    portSpeed: DataRate,
    nPorts: Int,
    routPolicy: RoutPolicy,
    enModel: EnModel<Switch>,
    flowTable: FlowTable,
    stabilizer: NetSimStabilizer,
) : Switch(ip, portSpeed, nPorts, routPolicy, enModel, flowTable, stabilizer), SerializableNode {

    override fun toSpecs(): Specs<GlobalSwitch> =
        GlobalSwitchSpecs(
//            id = id,
            portSpeed = portSpeed,
            nPorts = nPorts,
        )

    override fun toString(): String = "GlobalSwitch(ip=$ip)"

    companion object {
        context(NetSimScope)
        internal suspend operator fun invoke(
            ip: IPv4Address? = null,
            subnet: IPv4Address? = addrMngr.globalPrefix,
            portSpeed: DataRate? = null,
            nPorts: Int? = null,
            enModel: EnModel<Switch>? = null,
        ): GlobalSwitch {
            val nodeConfig = devConfig.nodeConfig
            val switchConfig = devConfig.nodeConfig.switchConfig
            val coreSwitchConfig = nodeConfig.coreSwitchConfig

            // Assert both subnet has been claimed.
            subnet?.let { assert(addrMngr.isAddrClaimed(it)) }

            // Assert `ip` is not a subnet.
            ip?.let { assert(it.isPrefixBlock.not()) }

            return GlobalSwitch(
                ip = ip?.let {
                    // Claim ip.
                    addrMngr.claimIp(ip)
                    // Assert `subnet` is the most specific subnet `ip` is in.
                    assert(addrMngr.getSubnetOf(ip) == subnet)
                    it

                // Retrieve new unused ip address in subnet (subnet can also be 0.0.0.0/0)
                } ?: addrMngr.getNewIp(subNet = subnet),
                portSpeed = portSpeed
                    ?: coreSwitchConfig.defaultPortSpeed
                    ?: switchConfig.defaultPortSpeed
                    ?: nodeConfig.defaultPortSpeed!!,
                nPorts = nPorts
                    ?: coreSwitchConfig.defaultNPorts
                    ?: switchConfig.defaultNPorts
                    ?: nodeConfig.defaultNPorts!!,
                routPolicy = this@NetSimScope.config.routPolicy,
                enModel = enModel
                    ?: coreSwitchConfig.defaultEnModel
                    ?: switchConfig.defaultEnModel
                    ?: nodeConfig.defaultEnModel,
                flowTable = nodeConfig.flowTableVersion(),
                stabilizer = barrier.stabilizer()
            ).also { gs ->
                    gs.invalidate()
                    gs.netLaunch()
            }
        }
    }
}
