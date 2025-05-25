package org.opendc.simulator.network.utils

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.opendc.simulator.network.components.networks.NetworkSpecs
import org.opendc.simulator.network.components.networks.clos.ClosSpecs
import org.opendc.simulator.network.components.networks.custom.CustomNetworkSpecs
import org.opendc.simulator.network.components.networks.dragonfly.DragonFlySpecs
import org.opendc.simulator.network.components.networks.ftree.FTreeConfig
import org.opendc.simulator.network.components.networks.ftree.FatTreeSpecs
import org.opendc.simulator.network.components.specs.GlobalSwitchSpecs
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.components.specs.NodeSpecs
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.fairness.MaxMin
import org.opendc.simulator.network.policies.fairness.Proportional
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.OSPF
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.policies.routing.UGALL
import org.opendc.simulator.network.repl.synthetic.BitComplement
import org.opendc.simulator.network.repl.synthetic.BitReversal
import org.opendc.simulator.network.repl.synthetic.BitShuffle
import org.opendc.simulator.network.repl.synthetic.Full
import org.opendc.simulator.network.repl.synthetic.SyntheticWl
import org.opendc.simulator.network.repl.synthetic.adversarial.AdversarialWl
import org.opendc.simulator.network.repl.synthetic.adversarial.DFAdv
import org.opendc.simulator.network.repl.synthetic.adversarial.FTreeAdv
import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.routing.IPv4AddressSerializer
import org.opendc.simulator.network.simscope.NetConfig

/**
 * TODO
 *
 * Using a [SerializersModule] allows to avoid needing sealed interfaces in some cases,
 * improving flexibility in package organization.
 */
public val NETWORK_SERIALIZERS_MODULE: SerializersModule = SerializersModule {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Specs
    ////// Intellisense may give stack overflow error
    ////// in analyzing this code due to serialization of generic class,
    ////// but should compile just fine.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//    polymorphic(Specs::class) {
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        // Network Specifications
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        subclass(FatTreeSpecs::class)
//        subclass(ClosSpecs::class)
//        subclass(DragonFlySpecs::class)
//        subclass(CustomNetworkSpecs::class)
//
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        // Node Specifications
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        subclass(SwitchSpecs::class)
//        subclass(GlobalSwitchSpecs::class)
//        subclass(HostNodeSpecs::class)
//    }

    polymorphic(NodeSpecs::class) {
        subclass(SwitchSpecs::class)
        subclass(GlobalSwitchSpecs::class)
        subclass(HostNodeSpecs::class)
    }

    polymorphic(NetworkSpecs::class) {
        subclass(FatTreeSpecs::class)
        subclass(ClosSpecs::class)
        subclass(DragonFlySpecs::class)
        subclass(CustomNetworkSpecs::class)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Routing Policy
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    polymorphic(RoutPolicy::class) {
        subclass(ECMP::class)
        subclass(OSPF::class)
        subclass(UGALL::class)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fairness Policy
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    polymorphic(FairnessPolicy::class) {
        subclass(FirstComeFirstServed::class)
        subclass(MaxMin::class)
        subclass(Proportional::class)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Synthetic Workload
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    polymorphic(SyntheticWl::class) {
        subclass(BitComplement::class)
        subclass(BitReversal::class)
        subclass(BitShuffle::class)
        subclass(Full::class)
        subclass(FTreeAdv::class)
        subclass(DFAdv::class)
        polymorphic(AdversarialWl::class) {
            subclass(FTreeAdv::class)
            subclass(DFAdv::class)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // IPv4Address
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    contextual(IPv4Address::class, IPv4AddressSerializer())

    polymorphic(NetConfig::class) {
        subclass(FTreeConfig::class)
    }
}

public val NETWORK_JSON: Json = Json {
    serializersModule = NETWORK_SERIALIZERS_MODULE
}
