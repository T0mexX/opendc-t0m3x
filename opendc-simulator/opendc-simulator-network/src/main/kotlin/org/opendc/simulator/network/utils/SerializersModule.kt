package org.opendc.simulator.network.utils

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.opendc.simulator.network.components.networks.NetworkSpecs
import org.opendc.simulator.network.components.networks.clos.ClosSpecs
import org.opendc.simulator.network.components.networks.custom.CustomNetworkSpecs
import org.opendc.simulator.network.components.networks.dragonfly.DFSpecs
import org.opendc.simulator.network.components.networks.ftree.FatTreeSpecs
import org.opendc.simulator.network.components.specs.GlobalSwitchSpecs
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.components.specs.NodeSpecs
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.fairness.MaxMin
import org.opendc.simulator.network.policies.fairness.Proportional
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.MIN
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.policies.routing.UGALL
import org.opendc.simulator.network.repl.synthetic.SWLBitComplement
import org.opendc.simulator.network.repl.synthetic.SWLBitReversal
import org.opendc.simulator.network.repl.synthetic.SWLBitShuffle
import org.opendc.simulator.network.repl.synthetic.SWLFull
import org.opendc.simulator.network.repl.synthetic.SyntheticWl
import org.opendc.simulator.network.repl.synthetic.adversarial.SWLDFAdv
import org.opendc.simulator.network.repl.synthetic.ftree.SWLFTreeAdv
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.policies.routing.UGALL2
import org.opendc.simulator.network.repl.synthetic.SWLRandom
import org.opendc.simulator.network.repl.synthetic.SWLRandPerm
import org.opendc.simulator.network.repl.synthetic.ftree.SWLFTreePodShift
import org.opendc.simulator.network.routing.IPv4AddressSerializer

/**
 * TODO
 *
 * Using a [SerializersModule] allows to avoid needing sealed interfaces in some cases,
 * improving flexibility in package organization.
 */
public val NETWORK_SERIALIZERS_MODULE: SerializersModule = SerializersModule {
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //// Specs
    //// Intellisense may give stack overflow error
    //// in analyzing this code due to serialization of generic class,
    //// but should compile just fine.
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//    polymorphic(org.opendc.simulator.network.components.specs.Specs::class) {
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        // Network Specifications
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        subclass(FatTreeSpecs::class)
//        subclass(ClosSpecs::class)
//        subclass(DFSpecs::class)
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
        subclass(DFSpecs::class)
        subclass(CustomNetworkSpecs::class)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Routing Policy
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    polymorphic(RoutPolicy::class) {
        subclass(ECMP::class)
        subclass(MIN::class)
        subclass(UGALL::class)
        subclass(UGALL2::class)
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
        subclass(SWLBitComplement::class)
        subclass(SWLBitReversal::class)
        subclass(SWLBitShuffle::class)
        subclass(SWLFull::class)
        subclass(SWLFTreeAdv::class)
        subclass(SWLDFAdv::class)
        subclass(SWLFTreePodShift::class)
        subclass(SWLRandom::class)
        subclass(SWLRandPerm::class)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // IPv4Address
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    contextual(IPv4Address::class, IPv4AddressSerializer())
}

public val NETWORK_JSON: Json = Json {
    serializersModule = NETWORK_SERIALIZERS_MODULE
}
