package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.FatTree
import org.opendc.simulator.network.simscope.NetSimScope

@Serializable
@SerialName("fat-tree-specs")
internal data class FatTreeSpecs(
    val name: String = "Default",
    val switchSpecs: SwitchSpecs? = null,
    val coreSwitchSpecs: SwitchSpecs? = null,
    val aggrSwitchSpecs: SwitchSpecs? = null,
    val torSwitchSpecs: SwitchSpecs? = null,
    val hostNodeSpecs: HostNodeSpecs,
) : Specs<FatTree> {
    /**
     * Returns a [FatTree] if the specs are valid, throws error otherwise.
     */

    context(NetSimScope)
    override suspend fun build(): FatTree =
        FatTree(
            coreSpecs = coreSwitchSpecs?.toCoreSwitchSpecs()
                ?: switchSpecs?.toCoreSwitchSpecs()!!,
            aggrSpecs = aggrSwitchSpecs ?: switchSpecs!!,
            torSpecs = torSwitchSpecs ?: switchSpecs!!,
            hostNodeSpecs = hostNodeSpecs,
        )
}
