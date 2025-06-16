/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.utils

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.opendc.simulator.network.components.networks.custom.CustomNetworkSpecs
import org.opendc.simulator.network.components.networks.dragonfly.DFSpecs
import org.opendc.simulator.network.components.networks.flatfly.FlatFlySpecs
import org.opendc.simulator.network.components.networks.ftree.FatTreeSpecs
import org.opendc.simulator.network.components.node.switchh.SwitchSpecs
import org.opendc.simulator.network.components.node.terminal.TerminalSpecs
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.fairness.MaxMin
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.MIN
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.policies.routing.UGALL
import org.opendc.simulator.network.repl.synthetictraffic.STrafficBitComplement
import org.opendc.simulator.network.repl.synthetictraffic.STrafficBitReversal
import org.opendc.simulator.network.repl.synthetictraffic.STrafficBitShuffle
import org.opendc.simulator.network.repl.synthetictraffic.STrafficBitTranspose
import org.opendc.simulator.network.repl.synthetictraffic.STrafficRandom
import org.opendc.simulator.network.repl.synthetictraffic.STrafficRandomPerm
import org.opendc.simulator.network.repl.synthetictraffic.STrafficUniform
import org.opendc.simulator.network.repl.synthetictraffic.df.STrafficDFAdv
import org.opendc.simulator.network.repl.synthetictraffic.ftree.STrafficFTreeAdv
import org.opendc.simulator.network.repl.synthetictraffic.ftree.STrafficFTreePodShift
import org.opendc.simulator.network.simscope.ip.IPv4AddressSerializer

/**
 * TODO
 *
 * Using a [SerializersModule] allows to avoid needing sealed interfaces in some cases,
 * improving flexibility in package organization.
 */
public val NETWORK_SERIALIZERS_MODULE: SerializersModule =
    SerializersModule {
        // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // // Specs
        // // Intellisense may give stack overflow error
        // // in analyzing this code due to serialization of generic class,
        // // but should compile just fine.
        // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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

        polymorphic(SerialNodeSpecs::class) {
            subclass(SwitchSpecs::class)
            subclass(TerminalSpecs::class)
        }

        polymorphic(SerialNetSpecs::class) {
            subclass(FatTreeSpecs::class)
            subclass(DFSpecs::class)
            subclass(CustomNetworkSpecs::class)
            subclass(FlatFlySpecs::class)
        }

        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Routing Policy
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        polymorphic(RoutPolicy::class) {
            subclass(ECMP::class)
            subclass(MIN::class)
            subclass(UGALL::class)
        }

        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Fairness Policy
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        polymorphic(FairnessPolicy::class) {
            subclass(FirstComeFirstServed::class)
            subclass(MaxMin::class)
        }

        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Synthetic Workload
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        polymorphic(SerialSTraffic::class) {
            subclass(STrafficBitComplement::class)
            subclass(STrafficBitReversal::class)
            subclass(STrafficBitShuffle::class)
            subclass(STrafficBitTranspose::class)
            subclass(STrafficRandomPerm::class)
            subclass(STrafficUniform::class)
            subclass(STrafficRandom::class)
            subclass(STrafficFTreeAdv::class)
            subclass(STrafficDFAdv::class)
            subclass(STrafficFTreePodShift::class)
        }

        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // IPv4Address
        // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        contextual(IPv4Address::class, IPv4AddressSerializer())
    }

public val NETWORK_JSON: Json =
    Json {
        serializersModule = NETWORK_SERIALIZERS_MODULE
    }

internal interface SerialNetSpecs

internal interface SerialNodeSpecs

internal interface SerialSTraffic
