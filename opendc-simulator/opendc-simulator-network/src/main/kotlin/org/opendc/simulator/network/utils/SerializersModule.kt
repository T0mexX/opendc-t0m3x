package org.opendc.simulator.network.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.FirstComeFirstServed
import org.opendc.simulator.network.policies.fairness.MaxMin
import org.opendc.simulator.network.policies.fairness.Proportional
import org.opendc.simulator.network.policies.routing.ECMP
import org.opendc.simulator.network.policies.routing.OSPF
import org.opendc.simulator.network.policies.routing.RoutPolicy
import org.opendc.simulator.network.policies.routing.UGALL

public val NETWORK_SERIALIZERS_MODULE: SerializersModule = SerializersModule {
    polymorphic(RoutPolicy::class) {
        subclass(ECMP::class)
        subclass(OSPF::class)
        subclass(UGALL::class)
    }
    polymorphic(FairnessPolicy::class) {
        subclass(FirstComeFirstServed::class)
        subclass(MaxMin::class)
        subclass(Proportional::class)
    }
}

public val NETWORK_JSON: Json = Json {
    serializersModule = NETWORK_SERIALIZERS_MODULE
}
