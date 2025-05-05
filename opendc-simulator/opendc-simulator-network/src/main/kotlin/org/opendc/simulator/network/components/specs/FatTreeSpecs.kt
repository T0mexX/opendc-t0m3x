package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.FatTree
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * TODO
 * @param k
 */
@Serializable
@SerialName("ftree")
internal data class FatTreeSpecs(
    val name: String = "Default",
    val k: Int,
    val switchSpecs: SwitchSpecs? = null,
    val crSwSpecs: SwitchSpecs = switchSpecs!!,
    val aggrSwSpecs: SwitchSpecs = switchSpecs!!,
    val accessSwSpecs: SwitchSpecs = switchSpecs!!,
    val hostSpecs: HostNodeSpecs,
) : Specs<FatTree> {

    init {
        require(k % 2 == 0)
    }

    /**
     * Returns a [FatTree] if the specs are valid, throws error otherwise.
     */
    context(NetSimScope)
    override suspend fun build(): FatTree =
        FatTree(this)
}
