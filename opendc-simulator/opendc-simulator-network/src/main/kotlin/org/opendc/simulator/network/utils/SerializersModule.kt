package org.opendc.simulator.network.utils

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.OSPF
import org.opendc.simulator.network.policies.routing.RoutPolicy

public val NETWORK_SERIALIZERS_MODULE: SerializersModule = SerializersModule {
    polymorphic(RoutPolicy::class) {
//        subclass(ECMP::class, ECMP.serializer())
        subclass(OSPF::class, OSPF.serializer())
    }
}
