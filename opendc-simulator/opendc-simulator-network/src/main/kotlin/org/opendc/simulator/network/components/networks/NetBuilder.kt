package org.opendc.simulator.network.components.networks

import org.opendc.simulator.network.components.specs.Specs
import org.opendc.simulator.network.simscope.NetSimScope

internal interface NetBuilder {
    context(NetSimScope)
    suspend operator fun invoke(specs: Specs<Network>): Network
}
