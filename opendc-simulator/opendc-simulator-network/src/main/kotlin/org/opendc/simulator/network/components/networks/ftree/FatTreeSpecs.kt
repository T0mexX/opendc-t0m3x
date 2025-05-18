package org.opendc.simulator.network.components.networks.ftree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.specs.HostNodeSpecs
import org.opendc.simulator.network.components.networks.NetworkSpecs
import org.opendc.simulator.network.components.specs.SwitchSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.pow

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
) : NetworkSpecs<FTree> {

    init {
        require(k % 2 == 0)
    }

    val nCoreSw: Int = (k.toDouble().pow(2.0) / 4).toInt()
    val nAggrSw: Int = (k.toDouble().pow(2.0) / 2).toInt()
    val nEdgeSw: Int = nAggrSw
    val nPods: Int = k
    override val R_: Int = (5 * k.toDouble().pow(2.0) / 4).toInt()
    override val N_: Int = (k.toDouble().pow(3.0) / 4).toInt()
    override val V_: Int = R_ + N_
    override val E_: Int = N_ + nEdgeSw * (k / 2) + nAggrSw * (k / 2)


    /**
     * Returns a [FTree] if the specs are valid, throws error otherwise.
     */
    context(NetSimScope)
    override suspend fun build(): FTree = FTree(this)
}
