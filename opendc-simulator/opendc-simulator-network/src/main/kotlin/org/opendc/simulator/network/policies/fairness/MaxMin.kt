package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.simulator.network.components.port.Port
import org.opendc.simulator.network.components.port.PortFlowEntry
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.simscope.NetSimConfig
import org.opendc.simulator.network.simscope.NetSimDevConfig
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import javax.naming.OperationNotSupportedException

@Serializable
@SerialName("maxmin")
internal data object MaxMin : FairnessPolicy {
    context(Port) override suspend fun applyPolicy(entryList: List<PortFlowEntry>) {
        TODO("Not yet implemented")
    }
}
